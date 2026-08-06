import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import MarketResponseResult from './MarketResponseResult.jsx';
import MessageVariantEditor from './MessageVariantEditor.jsx';
import PanelInterviewResult from './PanelInterviewResult.jsx';
import PersonaChoiceCards from './PersonaChoiceCards.jsx';
import QuestionEditor from './QuestionEditor.jsx';

function PersonaHarness() {
  const [selected, setSelected] = useState([]);
  const personas = [1, 2, 3, 4].map((id) => ({
    id,
    name: `Persona ${id}`,
    summary: `설명 ${id}`,
    recommended: id === 1,
    selected: id === 2,
  }));
  return <PersonaChoiceCards personas={personas} selectedIds={selected} onChange={setSelected} />;
}

function QuestionHarness() {
  const [questions, setQuestions] = useState(['질문 1', '질문 2', '질문 3']);
  return <QuestionEditor questions={questions} onChange={setQuestions} />;
}

function MessageHarness() {
  const [messages, setMessages] = useState([{ id: 'A', text: '' }]);
  return <MessageVariantEditor messages={messages} onChange={setMessages} />;
}

describe('validation components', () => {
  it('prevents selecting more than three personas', () => {
    render(<PersonaHarness />);
    [1, 2, 3, 4].forEach((id) => fireEvent.click(screen.getByLabelText(`Persona ${id}`)));
    expect(screen.getByLabelText('Persona 1')).toBeChecked();
    expect(screen.getByLabelText('Persona 2')).toBeChecked();
    expect(screen.getByLabelText('Persona 3')).toBeChecked();
    expect(screen.getByLabelText('Persona 4')).not.toBeChecked();
  });

  it('adds, removes, and reorders compact interview questions', () => {
    render(<QuestionHarness />);
    fireEvent.click(screen.getByRole('button', { name: '질문 추가' }));
    expect(screen.getByLabelText('질문 4')).toBeInTheDocument();
    const moveDown = screen.getAllByRole('button', { name: '아래로' });
    fireEvent.click(moveDown[0]);
    expect(screen.getByLabelText('질문 1')).toHaveValue('질문 2');
    fireEvent.click(screen.getAllByRole('button', { name: '삭제' })[0]);
    expect(screen.queryByLabelText('질문 4')).not.toBeInTheDocument();
  });

  it('limits message variants to three', () => {
    render(<MessageHarness />);
    const add = screen.getByRole('button', { name: '메시지 추가' });
    fireEvent.click(add);
    fireEvent.click(add);
    expect(screen.getByLabelText('메시지 C')).toBeInTheDocument();
    expect(add).toBeDisabled();
  });

  it('passes panel and market ids to the marketing route', () => {
    const panel = {
      interview: { id: 21 },
      summary: {},
      answers: [],
      disclaimer: '예상 결과',
    };
    const market = {
      prediction: { id: 31, panelInterviewId: 21 },
      summary: {},
      results: [],
      disclaimer: '상대 지표',
    };
    const { unmount } = render(
      <MemoryRouter initialEntries={['/app/projects/7/validate/interview/21']}>
        <Routes><Route path="/app/projects/:projectId/validate/interview/:interviewId" element={<PanelInterviewResult detail={panel} />} /></Routes>
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: '이 결과로 마케팅 콘텐츠 만들기' }))
      .toHaveAttribute('href', '/app/projects/7/journey/marketing?panelInterviewId=21');
    unmount();
    render(
      <MemoryRouter initialEntries={['/app/projects/7/validate/market-response/31']}>
        <Routes><Route path="/app/projects/:projectId/validate/market-response/:predictionId" element={<MarketResponseResult detail={market} />} /></Routes>
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: '이 결과로 마케팅 콘텐츠 만들기' }))
      .toHaveAttribute('href', '/app/projects/7/journey/marketing?marketResponseId=31&panelInterviewId=21');
  });
});
