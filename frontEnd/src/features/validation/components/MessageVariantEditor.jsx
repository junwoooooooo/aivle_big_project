import { Button, Textarea } from '../../../shared/ui/index.js';

export default function MessageVariantEditor({ messages, onChange, disabled = false }) {
  function update(index, text) {
    onChange(messages.map((item, current) => current === index ? { ...item, text } : item));
  }
  function remove(index) {
    onChange(messages
      .filter((_, current) => current !== index)
      .map((item, current) => ({ ...item, id: String.fromCharCode(65 + current) })));
  }
  return (
    <fieldset className="validation-messages" disabled={disabled}>
      <legend>비교 메시지 <span>{messages.length}/3</span></legend>
      {messages.map((item, index) => (
        <article key={item.id}>
          <Textarea label={`메시지 ${item.id}`} value={item.text} maxLength="300" onChange={(event) => update(index, event.target.value)} />
          <div>
            <span>{item.text.length}/300자</span>
            <Button type="button" size="small" variant="ghost" disabled={messages.length <= 1} onClick={() => remove(index)}>삭제</Button>
          </div>
        </article>
      ))}
      <Button
        type="button"
        variant="outline"
        disabled={messages.length >= 3}
        onClick={() => onChange([...messages, {
          id: String.fromCharCode(65 + messages.length),
          text: '',
        }])}
      >
        메시지 추가
      </Button>
    </fieldset>
  );
}
