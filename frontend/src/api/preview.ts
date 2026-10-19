export class PreviewRequestError extends Error {
  readonly code: string | null
  readonly status: number

  constructor(code: string | null, status: number) {
    super(`Preview failed with HTTP ${status}`)
    this.code = code
    this.status = status
  }
}

export async function previewFile(file: File, signal: AbortSignal): Promise<Blob> {
  const body = new FormData()
  body.append('file', file)

  const response = await fetch('/api/preview', { method: 'POST', body, signal })
  if (!response.ok) {
    const error: unknown = await response.json().catch(() => null)
    const code = error && typeof error === 'object' && 'code' in error && typeof error.code === 'string'
      ? error.code : null
    throw new PreviewRequestError(code, response.status)
  }
  return response.blob()
}
