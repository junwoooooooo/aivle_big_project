import { forwardRef, useId, useState } from 'react';

import './ui.css';

export const Button = forwardRef(function Button({
  children,
  variant = 'primary',
  size = 'medium',
  loading = false,
  disabled = false,
  className = '',
  ...props
}, ref) {
  return (
    <button
      ref={ref}
      className={`ui-button ui-button--${variant} ui-button--${size} ${className}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <Spinner label="처리 중" size="small" />}
      <span>{children}</span>
    </button>
  );
});

export function IconButton({ label, children, ...props }) {
  return (
    <Button
      variant="ghost"
      className="ui-icon-button"
      aria-label={label}
      {...props}
    >
      {children}
    </Button>
  );
}

export function FormField({
  id,
  label,
  description,
  error,
  required,
  children,
}) {
  const generatedId = useId();
  const fieldId = id || generatedId;
  const descriptionId = description ? `${fieldId}-description` : undefined;
  const errorId = error ? `${fieldId}-error` : undefined;
  const describedBy = [descriptionId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`ui-field ${error ? 'ui-field--error' : ''}`}>
      <label className="ui-field__label" htmlFor={fieldId}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      {description && (
        <div className="ui-field__description" id={descriptionId}>
          {description}
        </div>
      )}
      {children({ id: fieldId, 'aria-describedby': describedBy, 'aria-invalid': Boolean(error) })}
      {error && (
        <div className="ui-field__error" id={errorId}>
          {error}
        </div>
      )}
    </div>
  );
}

export const TextInput = forwardRef(function TextInput(
  { label, description, error, required, id, ...props },
  ref,
) {
  return (
    <FormField {...{ label, description, error, required, id }}>
      {(fieldProps) => <input ref={ref} className="ui-input" {...fieldProps} {...props} />}
    </FormField>
  );
});

export const PasswordInput = forwardRef(function PasswordInput({ revealLabel = '비밀번호 표시', ...props }, ref) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="ui-password">
      <TextInput ref={ref} type={visible ? 'text' : 'password'} {...props} />
      <button
        type="button"
        className="ui-password__toggle"
        aria-pressed={visible}
        onClick={() => setVisible((current) => !current)}
      >
        {visible ? '숨기기' : revealLabel}
      </button>
    </div>
  );
});

export function Textarea({ label, description, error, required, id, ...props }) {
  return (
    <FormField {...{ label, description, error, required, id }}>
      {(fieldProps) => <textarea className="ui-input ui-textarea" {...fieldProps} {...props} />}
    </FormField>
  );
}

export function Select({ label, description, error, required, id, children, ...props }) {
  return (
    <FormField {...{ label, description, error, required, id }}>
      {(fieldProps) => (
        <select className="ui-input" {...fieldProps} {...props}>
          {children}
        </select>
      )}
    </FormField>
  );
}

function Choice({ type, label, ...props }) {
  return (
    <label className="ui-choice">
      <input type={type} {...props} />
      <span>{label}</span>
    </label>
  );
}

export function Checkbox(props) {
  return <Choice type="checkbox" {...props} />;
}

export function Radio(props) {
  return <Choice type="radio" {...props} />;
}

export function FileInput({ label, description, error, required, id, ...props }) {
  return (
    <FormField {...{ label, description, error, required, id }}>
      {(fieldProps) => <input className="ui-file-input" type="file" {...fieldProps} {...props} />}
    </FormField>
  );
}

export function Spinner({ label = '불러오는 중', size = 'medium' }) {
  return (
    <span className={`ui-spinner ui-spinner--${size}`} role="status">
      <span className="ui-spinner__shape" aria-hidden="true" />
      <span className="visually-hidden">{label}</span>
    </span>
  );
}
