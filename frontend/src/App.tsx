import { useEffect, useLayoutEffect, useRef, useState, type ChangeEvent, type DragEvent, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from 'react'

type TargetFormat = 'PDF_A_1b' | 'PDF_A_2b' | 'PDF_A_3b'
type Status = 'idle' | 'converting' | 'done' | 'ready'
type Theme = 'light' | 'dark'
type ErrorDetail = { label: string; text: string }
type ConversionError = { message: string; details: ErrorDetail[] }

class ConversionRequestError extends Error {
  readonly details: ErrorDetail[]

  constructor(message: string, details: ErrorDetail[]) {
    super(message)
    this.details = details
  }
}

function readErrorDetails(value: unknown): ErrorDetail[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return []

  const labels: Record<string, string> = {
    log: 'Лог обработки',
    report: 'Отчёт проверки PDF/A',
  }
  return Object.entries(value).flatMap(([key, detail]) => {
    if (detail == null) return []
    const text = typeof detail === 'string' ? detail : JSON.stringify(detail, null, 2)
    return text?.trim() ? [{ label: labels[key] ?? key, text }] : []
  })
}

const formatLabels: Record<TargetFormat, string> = {
  PDF_A_1b: 'PDF/A-1b',
  PDF_A_2b: 'PDF/A-2b',
  PDF_A_3b: 'PDF/A-3b',
}
const formatOptions: TargetFormat[] = ['PDF_A_1b', 'PDF_A_2b', 'PDF_A_3b']
const checkDisplayMs = 650

function getFileExtension(name: string) {
  const dotIndex = name.lastIndexOf('.')
  return dotIndex > 0 && dotIndex < name.length - 1 ? name.slice(dotIndex + 1).toUpperCase() : 'FILE'
}

function FileTypeIcon({ name }: { name: string }) {
  const extension = getFileExtension(name)
  const kind = extension === 'PDF' ? 'pdf'
    : extension === 'DOC' || extension === 'DOCX' ? 'word'
      : extension === 'XLS' || extension === 'XLSX' ? 'excel'
        : extension === 'PPT' || extension === 'PPTX' ? 'powerpoint'
          : 'generic'
  const symbol = kind === 'word' ? 'W' : kind === 'excel' ? 'X' : kind === 'powerpoint' ? 'P' : extension

  return (
    <span className={`file-type-icon file-type-icon-${kind}`} aria-hidden="true" title={extension}>
      <svg viewBox="0 0 42 44" fill="none" strokeWidth="1.4" strokeLinejoin="round">
        <path className="file-type-sheet" d="M17 2.5h19a3 3 0 0 1 3 3v33a3 3 0 0 1-3 3H17a3 3 0 0 1-3-3v-33a3 3 0 0 1 3-3Z" />
        <path className="file-type-lines" d="M29 14h6m-6 6h6m-6 6h6m-15 7h15m-15 4h12" strokeLinecap="round" />
      </svg>
      <span className="file-type-badge">{symbol}</span>
    </span>
  )
}

function formatFileSize(bytes: number) {
  if (bytes === 0) return '0 КБ'
  return bytes < 1024 * 1024
    ? `${Math.max(1, Math.round(bytes / 1024))} КБ`
    : `${(bytes / (1024 * 1024)).toFixed(1)} МБ`
}

function getInitialTheme(): Theme {
  try {
    return window.localStorage.getItem('pdfa-theme') === 'dark' ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

function App() {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const formatDropdownRef = useRef<HTMLDivElement>(null)
  const formatButtonRef = useRef<HTMLButtonElement>(null)
  const formatOptionRefs = useRef<Record<TargetFormat, HTMLButtonElement | null>>({ PDF_A_1b: null, PDF_A_2b: null, PDF_A_3b: null })
  const dragDepth = useRef(0)
  const conversionTimer = useRef<number | null>(null)
  const conversionRequest = useRef<AbortController | null>(null)
  const result = useRef<{ url: string; name: string } | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [fileRevision, setFileRevision] = useState(0)
  const [target, setTarget] = useState<TargetFormat>('PDF_A_2b')
  const [isDragging, setIsDragging] = useState(false)
  const [status, setStatus] = useState<Status>('idle')
  const [serverError, setServerError] = useState<ConversionError | null>(null)
  const [theme, setTheme] = useState<Theme>(getInitialTheme)
  const [isFormatOpen, setIsFormatOpen] = useState(false)
  const isConverting = status === 'converting'
  const isReady = status === 'done' || status === 'ready'
  const availableFormats = formatOptions.filter((format) => format !== target)

  useLayoutEffect(() => {
    document.documentElement.dataset.theme = theme
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#111722' : '#f7f8fa')
    try {
      window.localStorage.setItem('pdfa-theme', theme)
    } catch {
      // Тема остаётся переключаемой, даже если хранилище браузера недоступно.
    }
  }, [theme])

  useEffect(() => () => {
    if (conversionTimer.current !== null) window.clearTimeout(conversionTimer.current)
    conversionRequest.current?.abort()
    if (result.current) URL.revokeObjectURL(result.current.url)
  }, [])

  function clearResult() {
    if (result.current) URL.revokeObjectURL(result.current.url)
    result.current = null
    if (conversionTimer.current !== null) window.clearTimeout(conversionTimer.current)
    conversionTimer.current = null
  }

  useEffect(() => {
    if (!isFormatOpen) return
    const firstAvailable = formatOptions.find((format) => format !== target)
    if (firstAvailable) formatOptionRefs.current[firstAvailable]?.focus()

    function handleOutsideClick(event: PointerEvent) {
      if (!formatDropdownRef.current?.contains(event.target as Node)) setIsFormatOpen(false)
    }

    document.addEventListener('pointerdown', handleOutsideClick)
    return () => document.removeEventListener('pointerdown', handleOutsideClick)
  }, [isFormatOpen, target])

  function selectTarget(format: TargetFormat) {
    clearResult()
    setTarget(format)
    setStatus('idle')
    setServerError(null)
    setIsFormatOpen(false)
    formatButtonRef.current?.focus()
  }

  function handleFormatOptionKeyDown(event: ReactKeyboardEvent<HTMLButtonElement>, index: number) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const direction = event.key === 'ArrowDown' ? 1 : -1
      const nextFormat = availableFormats[(index + direction + availableFormats.length) % availableFormats.length]
      formatOptionRefs.current[nextFormat]?.focus()
    }
    if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      formatOptionRefs.current[availableFormats[event.key === 'Home' ? 0 : availableFormats.length - 1]]?.focus()
    }
  }

  function selectFile(nextFile?: File) {
    setServerError(null)
    if (!nextFile) return
    clearResult()
    setFile(nextFile)
    setFileRevision((revision) => revision + 1)
    setStatus('idle')
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    selectFile(event.target.files?.[0])
    event.target.value = ''
  }

  function clearFile() {
    if (isConverting) return
    clearResult()
    setFile(null)
    setStatus('idle')
    setServerError(null)
    fileInputRef.current?.focus()
  }

  function handleDragEnter(event: DragEvent<HTMLDivElement>) {
    if (!event.dataTransfer.types.includes('Files')) return
    event.preventDefault()
    if (isConverting) return
    dragDepth.current += 1
    setIsDragging(true)
  }

  function handleDragLeave() {
    if (dragDepth.current === 0) return
    dragDepth.current = Math.max(0, dragDepth.current - 1)
    if (dragDepth.current === 0) setIsDragging(false)
  }

  function handleDragOver(event: DragEvent<HTMLDivElement>) {
    if (!event.dataTransfer.types.includes('Files')) return
    event.preventDefault()
    event.dataTransfer.dropEffect = isConverting ? 'none' : 'copy'
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    if (!event.dataTransfer.types.includes('Files') && event.dataTransfer.files.length === 0) return
    event.preventDefault()
    dragDepth.current = 0
    setIsDragging(false)
    if (isConverting) return
    selectFile(event.dataTransfer.files[0])
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file || isConverting) return
    if (isReady && result.current) {
      downloadResult()
      return
    }
    setIsFormatOpen(false)
    setStatus('converting')
    setServerError(null)
    const controller = new AbortController()
    conversionRequest.current = controller
    const formData = new FormData()
    formData.append('file', file)
    formData.append('target', target)

    try {
      const response = await fetch('/api/convert', {
        method: 'POST',
        body: formData,
        signal: controller.signal,
      })
      if (!response.ok) {
        let message = `Не удалось конвертировать файл (HTTP ${response.status}).`
        let details: ErrorDetail[] = []
        if (response.headers.get('content-type')?.includes('application/json')) {
          try {
            const error: unknown = await response.json()
            if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string') {
              message = error.message
            }
            if (error && typeof error === 'object' && 'details' in error) {
              details = readErrorDetails(error.details)
            }
          } catch {
            // Оставляем сообщение со статусом, если сервер вернул некорректный JSON.
          }
        }
        throw new ConversionRequestError(message, details)
      }

      const pdf = await response.blob()
      result.current = {
        url: URL.createObjectURL(pdf),
        name: file.name.replace(/\.[^/.]+$/, '') + '.pdf',
      }
      setStatus('done')
      conversionTimer.current = window.setTimeout(() => {
        setStatus('ready')
        conversionTimer.current = null
      }, checkDisplayMs)
    } catch (error) {
      if (controller.signal.aborted) return
      setStatus('idle')
      setServerError(error instanceof ConversionRequestError
        ? { message: error.message, details: error.details }
        : { message: error instanceof Error && !(error instanceof TypeError)
          ? error.message
          : 'Не удалось связаться с сервером. Попробуйте ещё раз.', details: [] })
    } finally {
      if (conversionRequest.current === controller) conversionRequest.current = null
    }
  }

  function downloadResult() {
    if (!result.current || !isReady) return
    const link = document.createElement('a')
    link.href = result.current.url
    link.download = result.current.name
    document.body.appendChild(link)
    link.click()
    link.remove()
  }

  return (
    <div
      className={`page-shell${isDragging ? ' page-shell-dragging' : ''}`}
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      <div className="page-drop-overlay" aria-hidden="true">
        <span>Отпустите файл в любом месте страницы</span>
      </div>
      <button
        className="theme-toggle"
        type="button"
        onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
        aria-label={theme === 'light' ? 'Включить тёмную тему' : 'Включить светлую тему'}
        aria-pressed={theme === 'dark'}
        title={theme === 'light' ? 'Включить тёмную тему' : 'Включить светлую тему'}
      >
        {theme === 'light' ? (
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42" />
          </svg>
        ) : (
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M20.5 14.3A8.5 8.5 0 0 1 9.7 3.5a8.5 8.5 0 1 0 10.8 10.8Z" />
          </svg>
        )}
      </button>

      <main className="main-content">
        <div className="intro">
          <h1>Конвертация в PDF/A</h1>
        </div>

        <form className="converter-card" onSubmit={handleSubmit}>
          <div className="card-heading">
            <div>
              <h2>Выберите файл</h2>
            </div>
          </div>

          <div
            className={`dropzone${file ? ' dropzone-compact' : ''}${isDragging ? ' dropzone-active' : ''}${isConverting ? ' dropzone-disabled' : ''}`}
          >
            <input
              ref={fileInputRef}
              id="source-file"
              className="file-input"
              type="file"
              onChange={handleFileChange}
              disabled={isConverting}
              aria-describedby={file ? 'replace-hint' : 'file-hint'}
            />
            <label htmlFor="source-file" className="dropzone-label">
              <span className="upload-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M12 16V4m0 0L7.5 8.5M12 4l4.5 4.5" />
                  <path d="M4.5 15.5v3A1.5 1.5 0 0 0 6 20h12a1.5 1.5 0 0 0 1.5-1.5v-3" />
                </svg>
              </span>
              {file ? (
                <span key={fileRevision} className="dropzone-file">
                  <FileTypeIcon name={file.name} />
                  <span className="file-details">
                    <strong title={file.name}>{file.name}</strong>
                    <span>{formatFileSize(file.size)}</span>
                  </span>
                </span>
              ) : (
                <>
                  <span className="dropzone-title">Перетащите файл сюда</span>
                  <span className="dropzone-subtitle">или <span className="choose-file">выберите на компьютере</span></span>
                  <span id="file-hint" className="dropzone-hint">PDF · DOC · DOCX · PPTX · XLS · XLSX</span>
                </>
              )}
            </label>
            {file && (
              <button className="clear-file" type="button" onClick={clearFile} disabled={isConverting} aria-label={`Убрать ${file.name}`} title="Убрать файл">
                ×
              </button>
            )}
          </div>
          {file && <p id="replace-hint" className="dropzone-help">Чтобы заменить файл, нажмите на него или перетащите сюда другой.</p>}

          <div className="form-divider" aria-hidden="true" />
          <div className="format-section">
            <div>
              <label className="field-label" htmlFor="target-format">Формат результата</label>
              <p className="field-description">Выберите стандарт для архивной копии.</p>
            </div>
            <div
              ref={formatDropdownRef}
              className="format-dropdown"
              onBlur={(event) => {
                if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setIsFormatOpen(false)
              }}
              onKeyDown={(event) => {
                if (event.key === 'Escape' && isFormatOpen) {
                  event.preventDefault()
                  setIsFormatOpen(false)
                  formatButtonRef.current?.focus()
                }
              }}
            >
              <button
                ref={formatButtonRef}
                id="target-format"
                className="format-trigger"
                type="button"
                aria-haspopup="listbox"
                aria-expanded={isFormatOpen}
                aria-controls="target-options"
                onClick={() => setIsFormatOpen((open) => !open)}
                onKeyDown={(event) => {
                  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                    event.preventDefault()
                    setIsFormatOpen(true)
                  }
                }}
                disabled={isConverting}
              >
                <span>{formatLabels[target]}</span>
                <svg className="format-chevron" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                  <path d="m5 7.5 5 5 5-5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </button>
              <div id="target-options" className={`format-menu${isFormatOpen ? ' format-menu-open' : ''}`} role="listbox" aria-label="Формат результата" aria-hidden={!isFormatOpen}>
                {availableFormats.map((format, index) => (
                  <button
                    key={format}
                    ref={(element) => { formatOptionRefs.current[format] = element }}
                    className="format-option"
                    type="button"
                    role="option"
                    aria-selected={false}
                    tabIndex={isFormatOpen && index === 0 ? 0 : -1}
                    onClick={() => selectTarget(format)}
                    onKeyDown={(event) => handleFormatOptionKeyDown(event, index)}
                  >
                    <span>{formatLabels[format]}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>

          <button className={`convert-button${isConverting ? ' convert-button-converting' : ''}`} type="submit" disabled={!file || isConverting}>
            <span className="convert-button-content">
              <span className="convert-button-spacer" aria-hidden="true" />
              <span>{isConverting ? 'Конвертация' : isReady ? 'Скачать' : 'Конвертировать'}</span>
              <span className={`conversion-indicator${status === 'done' ? ' conversion-indicator-done' : ''}${status === 'ready' ? ' conversion-indicator-ready' : ''}`} aria-hidden="true">
                {status !== 'idle' && (
                  <>
                    <svg className="conversion-ring" viewBox="0 0 24 24" fill="none">
                      <circle className="conversion-ring-track" cx="12" cy="12" r="9" />
                      <circle className="conversion-ring-fill" cx="12" cy="12" r="9" />
                    </svg>
                    <svg className="conversion-check" viewBox="0 0 24 24" fill="none">
                      <circle cx="12" cy="12" r="10" fill="currentColor" />
                      <path d="m7 12 3.2 3.2L17 8.8" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                    <svg className="conversion-download" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M12 3.5v11m0 0 4-4m-4 4-4-4M4.5 17.5v2h15v-2" />
                    </svg>
                  </>
                )}
              </span>
            </span>
          </button>

          <div className="form-message">
            {serverError && (
              <div className="error-result">
                <p className="error-message" role="alert">{serverError.message}</p>
                {serverError.details.map((detail) => (
                  <div className="error-detail" key={detail.label}>
                    <strong>{detail.label}</strong>
                    <pre>{detail.text}</pre>
                  </div>
                ))}
              </div>
            )}
            <span role="status" aria-live="polite">
              {isConverting && <span className="sr-only">Файл конвертируется</span>}
              {isReady && <span className="sr-only">Файл готов к скачиванию</span>}
            </span>
          </div>
        </form>

      </main>
    </div>
  )
}

export default App
