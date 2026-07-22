import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './Admin.css';

import {
  useAuth,
} from '../auth/AuthContext.jsx';

const MENU = {
  HOME: 'home',
  SERVICE: 'service',
  PERSONA: 'persona',
  CRITERIA: 'criteria',
};

const initialServices = [
  {
    id: 1,
    name: '시장 분석',
    description: '시장 규모, 트렌드, 경쟁 환경을 분석합니다.',
    active: true,
  },
  {
    id: 2,
    name: 'BM 분석',
    description: '비즈니스 모델의 구조와 타당성을 분석합니다.',
    active: true,
  },
  {
    id: 3,
    name: '기술/운영 분석',
    description: '서비스 구현 가능성과 운영 구조를 분석합니다.',
    active: true,
  },
  {
    id: 4,
    name: '가상 시장 검증',
    description: 'AI 페르소나를 활용해 고객 반응을 검증합니다.',
    active: false,
  },
];

const initialPersonas = [
  {
    id: 1,
    name: '가격 민감형 소비자',
    target: '20대 대학생·취업 준비생',
    description: '구매 시 가격과 할인 여부를 우선적으로 고려합니다.',
    active: true,
  },
  {
    id: 2,
    name: '품질 우선형 소비자',
    target: '20~30대 직장인',
    description: '가격보다 품질, 사용 경험, 브랜드 신뢰를 중시합니다.',
    active: true,
  },
  {
    id: 3,
    name: '신제품 탐색형 소비자',
    target: '디지털 친화 소비자',
    description: '새로운 상품과 서비스를 빠르게 체험하는 성향입니다.',
    active: false,
  },
];

const initialCriteria = [
  {
    id: 1,
    category: '시장성',
    name: '시장 성장 가능성',
    weight: 30,
    description: '목표 시장의 성장률과 향후 수요 확장 가능성',
  },
  {
    id: 2,
    category: '사업성',
    name: '수익 구조 타당성',
    weight: 25,
    description: '수익원, 가격 정책 및 비용 구조의 실현 가능성',
  },
  {
    id: 3,
    category: '고객성',
    name: '고객 문제 적합도',
    weight: 25,
    description: '제안 서비스가 고객 문제를 해결하는 정도',
  },
  {
    id: 4,
    category: '실현성',
    name: '기술 및 운영 가능성',
    weight: 20,
    description: '기술 구현과 실제 운영 체계의 구축 가능성',
  },
];

function Toggle({ checked, onChange, label }) {
  return (
    <label className="admin-toggle">
      <input
        type="checkbox"
        checked={checked}
        onChange={onChange}
        aria-label={label}
      />
      <span className="admin-toggle-track">
        <span className="admin-toggle-thumb" />
      </span>
    </label>
  );
}

function AdminHome({ onSelectMenu }) {
  const cards = [
    {
      key: MENU.SERVICE,
      title: '서비스 운영 관리',
      description: '분석 서비스의 제공 상태와 운영 현황을 관리합니다.',
      icon: '⚙',
    },
    {
      key: MENU.PERSONA,
      title: '페르소나 관리',
      description: '시장 검증에 사용하는 AI 페르소나를 관리합니다.',
      icon: '◉',
    },
    {
      key: MENU.CRITERIA,
      title: '판단 기준 관리',
      description: 'AI 분석 결과에 적용할 평가 기준을 관리합니다.',
      icon: '⚖',
    },
  ];

  return (
    <section className="admin-home">
      <div className="admin-restricted-banner">
        제한된 구역입니다
      </div>

      <div className="admin-card-grid">
        {cards.map((card) => (
          <button
            type="button"
            className="admin-main-card"
            key={card.key}
            onClick={() => onSelectMenu(card.key)}
          >
            <div className="admin-card-title">{card.title}</div>
            <div className="admin-card-icon" aria-hidden="true">
              {card.icon}
            </div>
            <p>{card.description}</p>
          </button>
        ))}
      </div>
    </section>
  );
}

function ServiceManagement({ services, setServices }) {
  const [keyword, setKeyword] = useState('');

  const filteredServices = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();

    if (!normalizedKeyword) {
      return services;
    }

    return services.filter((service) =>
      `${service.name} ${service.description}`
        .toLowerCase()
        .includes(normalizedKeyword),
    );
  }, [keyword, services]);

  const toggleService = (serviceId) => {
    setServices((previous) =>
      previous.map((service) =>
        service.id === serviceId
          ? { ...service, active: !service.active }
          : service,
      ),
    );
  };

  return (
    <section>
      <div className="admin-section-header">
        <div>
          <span className="admin-section-chip">서비스 운영</span>
          <h2>서비스 운영 관리</h2>
          <p>사용자에게 제공되는 분석 서비스의 상태를 관리합니다.</p>
        </div>

        <div className="admin-summary">
          운영 중 {services.filter((service) => service.active).length}개
        </div>
      </div>

      <div className="admin-toolbar">
        <label className="admin-search">
          <span>서비스 검색</span>
          <input
            type="search"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="서비스명을 입력하세요"
          />
        </label>
      </div>

      <div className="admin-list">
        {filteredServices.map((service) => (
          <article className="admin-list-item" key={service.id}>
            <div className="admin-list-content">
              <div className="admin-list-title-row">
                <h3>{service.name}</h3>
                <span
                  className={
                    service.active
                      ? 'admin-status active'
                      : 'admin-status inactive'
                  }
                >
                  {service.active ? '운영 중' : '중지'}
                </span>
              </div>
              <p>{service.description}</p>
            </div>

            <Toggle
              checked={service.active}
              onChange={() => toggleService(service.id)}
              label={`${service.name} 운영 상태 변경`}
            />
          </article>
        ))}

        {filteredServices.length === 0 && (
          <div className="admin-empty">
            검색 조건에 해당하는 서비스가 없습니다.
          </div>
        )}
      </div>
    </section>
  );
}

function PersonaManagement({ personas, setPersonas }) {
  const [showForm, setShowForm] = useState(false);
  const [newPersona, setNewPersona] = useState({
    name: '',
    target: '',
    description: '',
  });

  const togglePersona = (personaId) => {
    setPersonas((previous) =>
      previous.map((persona) =>
        persona.id === personaId
          ? { ...persona, active: !persona.active }
          : persona,
      ),
    );
  };

  const handleCreatePersona = () => {
    const name = newPersona.name.trim();
    const target = newPersona.target.trim();
    const description = newPersona.description.trim();

    if (!name || !target || !description) {
      return;
    }

    setPersonas((previous) => [
      ...previous,
      {
        id: Date.now(),
        name,
        target,
        description,
        active: true,
      },
    ]);

    setNewPersona({
      name: '',
      target: '',
      description: '',
    });
    setShowForm(false);
  };

  return (
    <section>
      <div className="admin-section-header">
        <div>
          <span className="admin-section-chip">페르소나 관리</span>
          <h2>AI 페르소나 관리</h2>
          <p>가상 시장 검증에서 사용할 소비자 페르소나를 관리합니다.</p>
        </div>

        <button
          type="button"
          className="admin-primary-button"
          onClick={() => setShowForm((previous) => !previous)}
        >
          {showForm ? '등록 취소' : '+ 페르소나 추가'}
        </button>
      </div>

      {showForm && (
        <div className="admin-form-card">
          <div className="admin-form-grid">
            <label>
              <span>페르소나명</span>
              <input
                value={newPersona.name}
                onChange={(event) =>
                  setNewPersona((previous) => ({
                    ...previous,
                    name: event.target.value,
                  }))
                }
                placeholder="예: 편의성 우선형 소비자"
              />
            </label>

            <label>
              <span>대상 고객</span>
              <input
                value={newPersona.target}
                onChange={(event) =>
                  setNewPersona((previous) => ({
                    ...previous,
                    target: event.target.value,
                  }))
                }
                placeholder="예: 30대 직장인"
              />
            </label>
          </div>

          <label>
            <span>설명</span>
            <textarea
              value={newPersona.description}
              onChange={(event) =>
                setNewPersona((previous) => ({
                  ...previous,
                  description: event.target.value,
                }))
              }
              placeholder="페르소나의 소비 성향과 판단 특성을 입력하세요."
              rows={3}
            />
          </label>

          <div className="admin-form-actions">
            <button
              type="button"
              className="admin-primary-button"
              onClick={handleCreatePersona}
            >
              등록
            </button>
          </div>
        </div>
      )}

      <div className="admin-persona-grid">
        {personas.map((persona) => (
          <article className="admin-persona-card" key={persona.id}>
            <div className="admin-persona-card-header">
              <div className="admin-persona-avatar">
                {persona.name.slice(0, 1)}
              </div>
              <Toggle
                checked={persona.active}
                onChange={() => togglePersona(persona.id)}
                label={`${persona.name} 사용 상태 변경`}
              />
            </div>

            <h3>{persona.name}</h3>
            <span className="admin-target-label">{persona.target}</span>
            <p>{persona.description}</p>

            <div className="admin-card-footer">
              <span>{persona.active ? '사용 중' : '사용 중지'}</span>
              <button type="button" className="admin-text-button">
                상세 보기
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function CriteriaManagement({ criteria, setCriteria }) {
  const updateWeight = (criteriaId, weight) => {
    const normalizedWeight = Math.max(0, Math.min(100, Number(weight)));

    setCriteria((previous) =>
      previous.map((item) =>
        item.id === criteriaId
          ? { ...item, weight: normalizedWeight }
          : item,
      ),
    );
  };

  const totalWeight = criteria.reduce(
    (total, item) => total + Number(item.weight),
    0,
  );

  return (
    <section>
      <div className="admin-section-header">
        <div>
          <span className="admin-section-chip">판단 기준 관리</span>
          <h2>AI 판단 기준 관리</h2>
          <p>사업성 분석에 적용되는 평가 항목과 가중치를 조정합니다.</p>
        </div>

        <div
          className={
            totalWeight === 100
              ? 'admin-weight-summary valid'
              : 'admin-weight-summary invalid'
          }
        >
          가중치 합계 {totalWeight}%
        </div>
      </div>

      {totalWeight !== 100 && (
        <div className="admin-warning">
          판단 기준의 가중치 합계가 100%가 되도록 조정해 주세요.
        </div>
      )}

      <div className="admin-table-wrapper">
        <table className="admin-table">
          <thead>
            <tr>
              <th>분류</th>
              <th>판단 기준</th>
              <th>설명</th>
              <th>가중치</th>
            </tr>
          </thead>
          <tbody>
            {criteria.map((item) => (
              <tr key={item.id}>
                <td>
                  <span className="admin-category">{item.category}</span>
                </td>
                <td>{item.name}</td>
                <td>{item.description}</td>
                <td>
                  <div className="admin-weight-control">
                    <input
                      type="range"
                      min="0"
                      max="100"
                      value={item.weight}
                      onChange={(event) =>
                        updateWeight(item.id, event.target.value)
                      }
                      aria-label={`${item.name} 가중치`}
                    />
                    <input
                      type="number"
                      min="0"
                      max="100"
                      value={item.weight}
                      onChange={(event) =>
                        updateWeight(item.id, event.target.value)
                      }
                      aria-label={`${item.name} 가중치 직접 입력`}
                    />
                    <span>%</span>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="admin-save-area">
        <button
          type="button"
          className="admin-primary-button"
          onClick={() => window.alert('판단 기준이 임시 저장되었습니다.')}
        >
          변경 사항 저장
        </button>
      </div>
    </section>
  );
}

function Admin() {
  const navigate = useNavigate();
  const {
    user,
    logout,
  } = useAuth();
  const [selectedMenu, setSelectedMenu] = useState(MENU.HOME);
  const [services, setServices] = useState(initialServices);
  const [personas, setPersonas] = useState(initialPersonas);
  const [criteria, setCriteria] = useState(initialCriteria);

  const handleLogout = () => {
    logout();
    navigate('/', {
      replace: true,
    });
  };

  const menuItems = [
    { key: MENU.HOME, label: '관리자 홈' },
    { key: MENU.SERVICE, label: '서비스 운영' },
    { key: MENU.PERSONA, label: '페르소나 관리' },
    { key: MENU.CRITERIA, label: '판단 기준 관리' },
  ];

  const renderContent = () => {
    switch (selectedMenu) {
      case MENU.SERVICE:
        return (
          <ServiceManagement
            services={services}
            setServices={setServices}
          />
        );

      case MENU.PERSONA:
        return (
          <PersonaManagement
            personas={personas}
            setPersonas={setPersonas}
          />
        );

      case MENU.CRITERIA:
        return (
          <CriteriaManagement
            criteria={criteria}
            setCriteria={setCriteria}
          />
        );

      default:
        return <AdminHome onSelectMenu={setSelectedMenu} />;
    }
  };

  return (
    <div className="admin-page">
      <aside className="admin-sidebar">
        <button
          type="button"
          className="admin-home-button"
          onClick={() => navigate('/')}
          aria-label="홈으로 이동"
        >
          ⌂
        </button>

        <div className="admin-profile">
          <strong>{user?.name || '관리자'}</strong>
          <span>{user?.email || 'Restricted'}</span>
        </div>

        <nav className="admin-menu" aria-label="관리자 메뉴">
          {menuItems.map((menu) => (
            <button
              type="button"
              key={menu.key}
              className={
                selectedMenu === menu.key
                  ? 'admin-menu-button active'
                  : 'admin-menu-button'
              }
              onClick={() => setSelectedMenu(menu.key)}
            >
              {menu.label}
            </button>
          ))}
        </nav>
      </aside>

      <div className="admin-main">
        <header className="admin-topbar">
          <div>
            <span className="admin-topbar-label">ADMIN CONSOLE</span>
            <h1>페르소나 플랫폼 관리자</h1>
          </div>

          <div className="admin-topbar-actions">
            <button
              type="button"
              className="admin-switch-button"
              onClick={() => navigate('/dashboard')}
            >
              사용자 대시보드
            </button>
            <button
              type="button"
              className="admin-switch-button"
              onClick={() => navigate('/')}
            >
              공개 홈
            </button>
            <button
              type="button"
              className="admin-logout-button"
              onClick={handleLogout}
            >
              로그아웃
            </button>
          </div>
        </header>

        <main className="admin-content">{renderContent()}</main>
      </div>
    </div>
  );
}

export default Admin;
