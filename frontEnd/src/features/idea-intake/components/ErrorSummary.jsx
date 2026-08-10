export default function ErrorSummary({ errors, title = '입력 내용을 확인해 주세요.' }) {
  const entries = Object.entries(errors).filter(([, message]) => Boolean(message));
  if (entries.length === 0) return null;
  return (
    <section className="idea-error-summary" role="alert" aria-labelledby="idea-error-summary-title" tabIndex="-1">
      <h3 id="idea-error-summary-title">{title}</h3>
      <ul>{entries.map(([fieldId, message]) => <li key={fieldId}><a href={`#${fieldId}`}>{message}</a></li>)}</ul>
    </section>
  );
}
