const PDF_MAGIC = [0x25, 0x50, 0x44, 0x46, 0x2d];
export const MIN_PDF_BYTES = 64;

export class InvalidPdfError extends Error {
  constructor() {
    super('PDF 보고서를 만들지 못했습니다.');
    this.name = 'InvalidPdfError';
    this.code = 'INVALID_PDF_DOCUMENT';
  }
}

export async function validatePdfBlob(blob) {
  if (!(blob instanceof Blob) || blob.size < MIN_PDF_BYTES) {
    throw new InvalidPdfError();
  }
  const signature = new Uint8Array(await blob.slice(0, PDF_MAGIC.length).arrayBuffer());
  if (PDF_MAGIC.some((value, index) => signature[index] !== value)) throw new InvalidPdfError();
  return blob;
}

export async function downloadPdfBlob(blob, filename) {
  const validated = await validatePdfBlob(blob);
  const url = URL.createObjectURL(validated);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.hidden = true;
  document.body.append(anchor);
  try { anchor.click(); } finally {
    anchor.remove();
    globalThis.setTimeout(() => URL.revokeObjectURL(url), 30_000);
  }
}
