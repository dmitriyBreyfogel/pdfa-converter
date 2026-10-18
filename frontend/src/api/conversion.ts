export type TargetFormat = 'PDF_A_1b' | 'PDF_A_2b' | 'PDF_A_3b'

export class ConversionRequestError extends Error {
  readonly code: string | null
  readonly status: number
  readonly report: string | null

  constructor(
    code: string | null,
    status: number,
    report: string | null,
  ) {
    super(`Conversion failed with HTTP ${status}`)
    this.code = code
    this.status = status
    this.report = report
  }
}

function readError(response: unknown, status: number): ConversionRequestError {
  if (!response || typeof response !== 'object') {
    return new ConversionRequestError(null, status, null)
  }

  const body = response as Record<string, unknown>
  const code = typeof body.code === 'string' ? body.code : null
  const details = body.details
  const report = code === 'PDF_A_VALIDATION_FAILED'
    && details && typeof details === 'object' && !Array.isArray(details)
    && 'report' in details && typeof details.report === 'string' && details.report.trim()
    ? details.report
    : null

  return new ConversionRequestError(code, status, report)
}

export async function convertFile(file: File, target: TargetFormat, signal: AbortSignal): Promise<Blob> {
  const body = new FormData()
  body.append('file', file)
  body.append('target', target)

  const response = await fetch('/api/convert', { method: 'POST', body, signal })
  if (!response.ok) {
    const error: unknown = await response.json().catch(() => null)
    throw readError(error, response.status)
  }

  return response.blob()
}
