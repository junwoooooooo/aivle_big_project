import { copyFile, mkdir, readFile, readdir, stat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, extname, join, resolve } from 'node:path';

const repositoryRoot = resolve(import.meta.dirname, '..', '..');
const sourceDirectories = {
  guide: join(repositoryRoot, 'docs', 'guide'),
  example: join(repositoryRoot, 'docs', 'example'),
};
const outputDirectory = join(import.meta.dirname, '..', 'public', 'resources', 'business-plan');

async function findSingleDocx(directory, label) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = entries.filter((entry) => entry.isFile() && extname(entry.name).toLowerCase() === '.docx');
  if (files.length !== 1) {
    throw new Error(`${label} 원본 DOCX를 정확히 하나 찾을 수 없습니다: ${directory}`);
  }
  const source = join(directory, files[0].name);
  const metadata = await stat(source);
  if (metadata.size <= 0) throw new Error(`${label} 원본 DOCX가 비어 있습니다: ${source}`);
  return source;
}

async function copyAndVerify(source, target) {
  await mkdir(dirname(target), { recursive: true });
  await copyFile(source, target);
  const [sourceBuffer, targetBuffer] = await Promise.all([readFile(source), readFile(target)]);
  const digest = (buffer) => createHash('sha256').update(buffer).digest('hex');
  if (sourceBuffer.length === 0 || sourceBuffer.length !== targetBuffer.length || digest(sourceBuffer) !== digest(targetBuffer)) {
    throw new Error(`복사 검증에 실패했습니다: ${target}`);
  }
}

const [guide, example] = await Promise.all([
  findSingleDocx(sourceDirectories.guide, '작성 가이드'),
  findSingleDocx(sourceDirectories.example, '작성 예시'),
]);

await Promise.all([
  copyAndVerify(guide, join(outputDirectory, 'business-plan-guide.docx')),
  copyAndVerify(example, join(outputDirectory, 'business-plan-example.docx')),
]);
