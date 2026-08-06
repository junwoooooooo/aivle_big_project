export default function AuthCard({ children, description, title }) {
  return <section className="auth-card" aria-labelledby="auth-card-title"><header><h2 id="auth-card-title">{title}</h2><p>{description}</p></header>{children}</section>;
}
