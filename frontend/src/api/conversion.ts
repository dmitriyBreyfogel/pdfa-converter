export type TargetFormat = 'PDF_A_1b' | 'PDF_A_2b' | 'PDF_A_3b'

type ApiError = {
  message?: string
}

export async function convertFile(file: File, target: TargetFormat, signal: AbortSignal): Promise<Blob> {
  const body = new FormData()
  body.append('file', file)
  body.append('target', target)

  const response = await fetch('/api/convert', { method: 'POST', body, signal })
  if (!response.ok) {
    const error = await response.json().catch(() => null) as ApiError | null
    throw new Error(error?.message || `Не удалось конвертировать файл (HTTP ${response.status})`)
  }

  return response.blob()
}
