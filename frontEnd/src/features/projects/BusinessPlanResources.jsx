import { AppIcon } from '../../shared/ui/index.js';

export function ResourceDownload({ resource, compact = false }) {
  return <a className={`resource-download ${compact ? 'resource-download--compact' : ''}`} href={resource.href} download={resource.download}><AppIcon name={resource.icon} /><span><strong>{resource.title}</strong>{!compact && <small>{resource.description}</small>}</span><em>DOCX</em><AppIcon name="download" /></a>;
}
