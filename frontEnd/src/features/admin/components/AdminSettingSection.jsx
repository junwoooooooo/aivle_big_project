import AdminSettingRow from './AdminSettingRow.jsx';

export default function AdminSettingSection({
  id,
  title,
  description,
  settings,
  onChange,
  severity = 'normal',
}) {
  return (
    <section
      className={`admin-setting-section admin-setting-section--${severity}`}
      aria-labelledby={`${id}-title`}
    >
      <header>
        <h2 id={`${id}-title`}>{title}</h2>
        <p>{description}</p>
      </header>
      <div className="admin-setting-section__rows">
        {settings.map((setting) => (
          <AdminSettingRow key={setting.key} setting={setting} onChange={onChange} />
        ))}
      </div>
    </section>
  );
}
