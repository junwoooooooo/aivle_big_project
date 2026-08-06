export default function AdminMetricCard({ label, value, description }) {
  return (
    <article className="admin-metric">
      <span>{label}</span>
      <strong>{value ?? '—'}</strong>
      {description && <small>{description}</small>}
    </article>
  );
}
