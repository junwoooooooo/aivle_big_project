export default function AdminPageHeader({ title, description }) {
  return (
    <header className="admin-page-header">
      <h1>{title}</h1>
      {description && <p>{description}</p>}
    </header>
  );
}
