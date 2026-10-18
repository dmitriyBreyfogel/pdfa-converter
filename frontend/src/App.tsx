import { useEffect, useLayoutEffect, useRef, useState, type ChangeEvent, type DragEvent, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { convertFile, ConversionRequestError, type TargetFormat } from './api/conversion'

type Status = 'idle' | 'converting' | 'ready'
type Theme = 'light' | 'dark'
type ConversionFeedback = { message: string; report: string | null }

const formatLabels: Record<TargetFormat, string> = {
  PDF_A_1b: 'PDF/A-1b',
  PDF_A_2b: 'PDF/A-2b',
  PDF_A_3b: 'PDF/A-3b',
}
const formatOptions: TargetFormat[] = ['PDF_A_1b', 'PDF_A_2b', 'PDF_A_3b']

function describeConversionError(error: unknown): ConversionFeedback {
  if (error instanceof ConversionRequestError) {
    switch (error.code) {
      case 'PDF_A_VALIDATION_FAILED':
        return { message: 'Созданный файл не прошёл проверку PDF/A.', report: error.report }
      case 'INVALID_FILE':
        return { message: 'Не удалось прочитать выбранный файл. Проверьте его и попробуйте ещё раз.', report: null }
      case 'EMPTY_FILE':
        return { message: 'Выбранный файл пуст.', report: null }
      case 'FILE_TOO_LARGE':
        return { message: 'Размер файла превышает допустимые 50 МБ.', report: null }
      case 'INVALID_FILE_EXTENSION':
        return { message: 'У файла должно быть поддерживаемое расширение.', report: null }
      case 'UNSUPPORTED_EXTENSION':
        return { message: 'Формат выбранного файла не поддерживается.', report: null }
      case 'FILE_PROCESSING_ERROR':
        return { message: 'Не удалось конвертировать документ. Попробуйте ещё раз.', report: null }
      default:
        return { message: error.status === 413
          ? 'Размер файла превышает допустимые 50 МБ.'
          : 'Не удалось конвертировать документ. Попробуйте ещё раз.', report: null }
    }
  }

  return { message: error instanceof TypeError
    ? 'Не удалось связаться с сервером. Попробуйте ещё раз.'
    : 'Не удалось конвертировать документ. Попробуйте ещё раз.', report: null }
}

function FileTypeIcon({ name }: { name: string }) {
  const extension = name.split('.').at(-1)?.toUpperCase() ?? ''
  const kind = extension === 'PDF' ? 'pdf'
    : extension === 'DOC' || extension === 'DOCX' ? 'word'
      : extension === 'XLS' || extension === 'XLSX' ? 'excel'
        : extension === 'PPTX' ? 'powerpoint' : 'generic'
  const symbol = kind === 'word' ? 'W' : kind === 'excel' ? 'X' : kind === 'powerpoint' ? 'P' : extension.slice(0, 4)

  return (
    <span className={`file-type-icon file-type-icon-${kind}`} aria-hidden="true">
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
  const conversionRequest = useRef<AbortController | null>(null)
  const resultUrl = useRef<string | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [fileRevision, setFileRevision] = useState(0)
  const [target, setTarget] = useState<TargetFormat>('PDF_A_1b')
  const [isDragging, setIsDragging] = useState(false)
  const [status, setStatus] = useState<Status>('idle')
  const [serverError, setServerError] = useState<ConversionFeedback | null>(null)
  const [theme, setTheme] = useState<Theme>(getInitialTheme)
  const [isFormatOpen, setIsFormatOpen] = useState(false)
  const isConverting = status === 'converting'
  const isReady = status === 'ready'

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
    conversionRequest.current?.abort()
    if (resultUrl.current) URL.revokeObjectURL(resultUrl.current)
  }, [])

  useEffect(() => {
    if (!isFormatOpen) return
    formatOptionRefs.current[target]?.focus()

    function handleOutsideClick(event: PointerEvent) {
      if (!formatDropdownRef.current?.contains(event.target as Node)) setIsFormatOpen(false)
    }

    document.addEventListener('pointerdown', handleOutsideClick)
    return () => document.removeEventListener('pointerdown', handleOutsideClick)
  }, [isFormatOpen, target])

  function selectTarget(format: TargetFormat) {
    if (format !== target) {
      if (resultUrl.current) URL.revokeObjectURL(resultUrl.current)
      resultUrl.current = null
      setStatus('idle')
    }
    setTarget(format)
    setServerError(null)
    setIsFormatOpen(false)
    formatButtonRef.current?.focus()
  }

  function handleFormatOptionKeyDown(event: ReactKeyboardEvent<HTMLButtonElement>, index: number) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const direction = event.key === 'ArrowDown' ? 1 : -1
      const nextFormat = formatOptions[(index + direction + formatOptions.length) % formatOptions.length]
      formatOptionRefs.current[nextFormat]?.focus()
    }
    if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      formatOptionRefs.current[formatOptions[event.key === 'Home' ? 0 : formatOptions.length - 1]]?.focus()
    }
  }

  function selectFile(nextFile?: File) {
    setServerError(null)
    if (!nextFile) return
    if (resultUrl.current) URL.revokeObjectURL(resultUrl.current)
    resultUrl.current = null
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
    if (resultUrl.current) URL.revokeObjectURL(resultUrl.current)
    resultUrl.current = null
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

  function handleDragLeave(event: DragEvent<HTMLDivElement>) {
    if (!event.dataTransfer.types.includes('Files')) return
    event.preventDefault()
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
    if (isReady) {
      downloadResult()
      return
    }
    setIsFormatOpen(false)
    setStatus('converting')
    setServerError(null)
    const request = new AbortController()
    conversionRequest.current = request
    try {
      const pdf = await convertFile(file, target, request.signal)
      resultUrl.current = URL.createObjectURL(pdf)
      setStatus('ready')
    } catch (error) {
      if (request.signal.aborted) return
      setServerError(describeConversionError(error))
      setStatus('idle')
    } finally {
      conversionRequest.current = null
    }
  }

  function downloadResult() {
    if (!file || !resultUrl.current || !isReady) return
    const link = document.createElement('a')
    link.href = resultUrl.current
    link.download = file.name.replace(/\.[^.]+$/, '') + '.pdf'
    document.body.appendChild(link)
    link.click()
    link.remove()
  }

  function downloadValidationReport() {
    if (!file || !serverError?.report) return

    const url = URL.createObjectURL(new Blob([serverError.report], { type: 'application/xml;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `${file.name.replace(/\.[^/.]+$/, '')}-${target.toLowerCase().replaceAll('_', '-')}-verapdf.xml`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
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
        <span>Отпустите файл размером до 50 МБ</span>
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
              accept=".pdf,.doc,.docx,.pptx,.xls,.xlsx"
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
                {formatOptions.map((format, index) => (
                  <button
                    key={format}
                    ref={(element) => { formatOptionRefs.current[format] = element }}
                    className="format-option"
                    type="button"
                    role="option"
                    aria-selected={target === format}
                    tabIndex={isFormatOpen && target === format ? 0 : -1}
                    onClick={() => selectTarget(format)}
                    onKeyDown={(event) => handleFormatOptionKeyDown(event, index)}
                  >
                    <span>{formatLabels[format]}</span>
                    {target === format && <span aria-hidden="true">✓</span>}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <button className={`convert-button${isConverting ? ' convert-button-converting' : ''}`} type="submit" disabled={!file || isConverting}>
            <span className="convert-button-content">
              <span className="convert-button-spacer" aria-hidden="true" />
              <span>{isConverting ? 'Конвертация' : isReady ? 'Скачать' : 'Конвертировать'}</span>
              <span className={`conversion-indicator${status === 'ready' ? ' conversion-indicator-ready' : ''}`} aria-hidden="true">
                {status !== 'idle' && (
                  <>
                    <svg className="conversion-ring" viewBox="0 0 24 24" fill="none">
                      <circle className="conversion-ring-track" cx="12" cy="12" r="9" />
                      <circle className="conversion-ring-fill" cx="12" cy="12" r="9" />
                    </svg>
                    <svg className="conversion-download" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M12 3.5v11m0 0 4-4m-4 4-4-4M4.5 17.5v2h15v-2" />
                    </svg>
                  </>
                )}
              </span>
            </span>
          </button>

          <div className="form-message" role="status" aria-live="polite">
            {isConverting && <span className="sr-only">Файл конвертируется</span>}
            {isReady && <span className="sr-only">Файл готов к скачиванию</span>}
          </div>
        </form>

      </main>
      {serverError && (
        <aside className="error-toast" role="alert">
          <svg className="error-toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7.5v5.5m0 3.5h.01" />
          </svg>
          <div className="error-toast-copy">
            <strong>Ошибка конвертации</strong>
            <p>{serverError.message}</p>
            {serverError.report && (
              <button className="report-download" type="button" onClick={downloadValidationReport}>
                Скачать отчёт veraPDF (.xml)
              </button>
            )}
          </div>
          <button className="error-toast-close" type="button" onClick={() => setServerError(null)} aria-label="Закрыть сообщение об ошибке">
            <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
              <path d="m5 5 10 10M15 5 5 15" />
            </svg>
          </button>
        </aside>
      )}
    </div>
  )
}

export default App
