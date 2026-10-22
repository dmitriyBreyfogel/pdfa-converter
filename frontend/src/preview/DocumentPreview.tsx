import { useEffect, useRef, useState } from 'react'
import { previewFile, PreviewRequestError } from '../api/preview'

type PreviewState =
  | { status: 'idle' | 'loading' }
  | { status: 'ready'; url: string }
  | { status: 'error'; message: string }

function describePreviewError(error: unknown): string {
  if (error instanceof PreviewRequestError) {
    switch (error.code) {
      case 'EMPTY_FILE': return 'Выбранный файл пуст.'
      case 'FILE_TOO_LARGE': return 'Размер файла превышает допустимые 200 МБ.'
      case 'INVALID_FILE': return 'Не удалось прочитать выбранный файл.'
      case 'INVALID_FILE_EXTENSION':
      case 'UNSUPPORTED_EXTENSION': return 'Формат выбранного файла не поддерживается.'
      default: return error.status === 413
        ? 'Размер файла превышает допустимые 200 МБ.'
        : 'Не удалось подготовить предпросмотр. Попробуйте ещё раз.'
    }
  }
  return 'Не удалось загрузить предпросмотр. Попробуйте ещё раз.'
}

export default function DocumentPreview({ file, disabled, onOpenChange }: {
  file: File
  disabled: boolean
  onOpenChange: (open: boolean) => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const requestRef = useRef<AbortController | null>(null)
  const urlRef = useRef<string | null>(null)
  const [isOpen, setIsOpen] = useState(false)
  const [preview, setPreview] = useState<PreviewState>({ status: 'idle' })

  useEffect(() => () => {
    requestRef.current?.abort()
    if (urlRef.current) URL.revokeObjectURL(urlRef.current)
  }, [])

  async function loadPreview() {
    const request = new AbortController()
    requestRef.current = request
    setPreview({ status: 'loading' })

    try {
      const pdf = await previewFile(file, request.signal)
      if (request.signal.aborted) return
      const url = URL.createObjectURL(pdf)
      urlRef.current = url
      setPreview({ status: 'ready', url })
    } catch (error) {
      if (!request.signal.aborted) {
        setPreview({ status: 'error', message: describePreviewError(error) })
      }
    } finally {
      if (requestRef.current === request) requestRef.current = null
    }
  }

  function openPreview() {
    dialogRef.current?.showModal()
    setIsOpen(true)
    onOpenChange(true)
    if (preview.status === 'idle' || preview.status === 'error') void loadPreview()
  }

  function closePreview() {
    requestRef.current?.abort()
    requestRef.current = null
    if (preview.status === 'loading') setPreview({ status: 'idle' })
    setIsOpen(false)
    onOpenChange(false)
  }

  return (
    <>
      <button className="preview-button" type="button" onClick={openPreview} disabled={disabled}>
        Предпросмотр страниц
      </button>
      <dialog ref={dialogRef} className="preview-dialog" aria-labelledby="preview-title" onClose={closePreview}>
        <div className="preview-dialog-panel">
          <div className="preview-heading">
            <div className="preview-heading-copy">
              <h2 id="preview-title">Предпросмотр исходного документа</h2>
              <p title={file.name}>{file.name}</p>
            </div>
            <button className="preview-close" type="button" onClick={() => dialogRef.current?.close()} aria-label="Закрыть предпросмотр">
              <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
                <path d="m5 5 10 10M15 5 5 15" />
              </svg>
            </button>
          </div>

          <div className="preview-surface">
            {preview.status === 'loading' && (
              <div className="preview-placeholder" role="status">
                <span className="preview-spinner" aria-hidden="true" />
                <strong>Готовим страницы документа</strong>
                <span>Это может занять несколько секунд.</span>
              </div>
            )}
            {preview.status === 'error' && (
              <div className="preview-placeholder preview-placeholder-error" role="alert">
                <strong>Не удалось показать документ</strong>
                <span>{preview.message}</span>
                <button type="button" onClick={() => void loadPreview()}>Повторить</button>
              </div>
            )}
            {preview.status === 'ready' && isOpen && (
              <iframe src={preview.url} title={`Страницы исходного файла ${file.name}`} />
            )}
          </div>

          {preview.status === 'ready' && (
            <div className="preview-footer">
              <a className="preview-open" href={preview.url} target="_blank" rel="noopener noreferrer">Открыть отдельно</a>
            </div>
          )}
        </div>
      </dialog>
    </>
  )
}
