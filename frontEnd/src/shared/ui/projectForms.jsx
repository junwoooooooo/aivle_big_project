import { useId, useState } from 'react';

import { AppIcon } from './icons.jsx';

export function ProjectFormSection({ title, eyebrow, description, children, className = '' }) {
  const headingId = useId();
  return <section className={`project-form-section ${className}`} aria-labelledby={headingId}>
    <header>{eyebrow && <p>{eyebrow}</p>}<h3 id={headingId}>{title}</h3>{description && <span>{description}</span>}</header>
    <div className="project-form-layout">{children}</div>
  </section>;
}

export function ProjectFormRow({ id, label, description, error, required, children, className = '' }) {
  const generatedId = useId();
  const fieldId = id || generatedId;
  const descriptionId = description ? `${fieldId}-description` : undefined;
  const errorId = error ? `${fieldId}-error` : undefined;
  const describedBy = [descriptionId, errorId].filter(Boolean).join(' ') || undefined;
  return <div className={`project-form-row ${error ? 'has-error' : ''} ${className}`}>
    <label htmlFor={fieldId}>{label}{required && <span aria-hidden="true"> *</span>}</label>
    <div className="project-form-row__control">
      {children({ id: fieldId, 'aria-describedby': describedBy, 'aria-invalid': Boolean(error) })}
      {description && <p id={descriptionId} className="project-form-row__description">{description}</p>}
      {error && <p id={errorId} className="project-form-row__error">{error}</p>}
    </div>
  </div>;
}

export function ProjectFieldGroup({ children, columns = 2 }) {
  return <div className="project-field-group" style={{ '--project-field-columns': columns }}>{children}</div>;
}

function fileKey(file) {
  return `${file.name}-${file.size}-${file.lastModified ?? ''}`;
}

export function FileDropzone({ id, label = '파일 선택', description = '파일을 끌어 놓거나 선택하세요.',
  acceptLabel, files = [], onFilesChange, multiple = false, disabled = false, uploading = false, error, ...inputProps }) {
  const generatedId = useId();
  const inputId = id || generatedId;
  const [dragActive, setDragActive] = useState(false);
  const choose = (nextFiles) => onFilesChange?.(multiple ? Array.from(nextFiles ?? []) : Array.from(nextFiles ?? []).slice(0, 1));
  const remove = (target) => onFilesChange?.(files.filter((file) => file !== target));
  const interactionDisabled = disabled || uploading;
  return <div className={`project-file-dropzone ${dragActive ? 'is-drag-active' : ''} ${uploading ? 'is-uploading' : ''} ${files.length > 0 ? 'has-files' : ''} ${error ? 'has-error' : ''}`} aria-busy={uploading || undefined}>
    <label htmlFor={inputId} onDragEnter={(event) => { event.preventDefault(); if (!disabled) setDragActive(true); }}
      onDragOver={(event) => event.preventDefault()} onDragLeave={() => setDragActive(false)} onDrop={(event) => { event.preventDefault(); setDragActive(false); if (!interactionDisabled) choose(event.dataTransfer.files); }}>
      <input id={inputId} type="file" multiple={multiple} disabled={interactionDisabled} onChange={(event) => choose(event.target.files)} {...inputProps} />
      <AppIcon name="upload" size={25} />
      <strong>{uploading ? '파일을 업로드하고 있습니다' : description}</strong>
      {acceptLabel && <span>{acceptLabel}</span>}
      <span className="project-file-dropzone__button">{label}</span>
    </label>
    {error && <p role="alert">{error}</p>}
    {files.length > 0 && <ul aria-label="선택한 파일">{files.map((file) => <li key={fileKey(file)}><AppIcon name="file" /><span><strong>{file.name}</strong><small>{Math.max(1, Math.ceil(file.size / 1024)).toLocaleString()} KB</small></span><button type="button" aria-label={`${file.name} 제거`} onClick={() => remove(file)} disabled={interactionDisabled}><AppIcon name="trash" size={16} /></button></li>)}</ul>}
  </div>;
}
