import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { apiClient } from '../../shared/api/apiClient';
import { ReportPreview } from './components/ReportPreview';
import { LoadingState, Alert } from '../../shared/ui';

const InterimReportTestPage = () => {
    const { projectId } = useParams();
    const [reportData, setReportData] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchReport = async () => {
            setIsLoading(true);
            try {
                const response = await apiClient.get(`/projects/${projectId}/reports/interim`);
                setReportData(response.data);
            } catch (err) {
                setError(err);
                console.error("중간 보고서 데이터 로딩 실패:", err);
            } finally {
                setIsLoading(false);
            }
        };

        fetchReport();
    }, [projectId]);

    if (isLoading) {
        return <LoadingState>중간 보고서 데이터를 생성하고 있습니다...</LoadingState>;
    }

    if (error) {
        return (
            <div className="p-8">
                <Alert type="error" title="오류 발생">
                    중간 보고서 데이터를 불러오는 중 오류가 발생했습니다.
                    <pre className="mt-2 text-xs whitespace-pre-wrap">
                        {error.message}
                    </pre>
                </Alert>
            </div>
        );
    }

    if (!reportData) {
        return (
            <div className="p-8">
                <Alert type="info">표시할 보고서 데이터가 없습니다.</Alert>
            </div>
        );
    }

    return (
        <div>
            <div className="bg-slate-800 text-white p-4 text-center text-sm">
                <p>
                    <strong>테스트 페이지:</strong> 이 페이지는 중간 보고서 UI 컴포넌트를 테스트하기 위한 페이지입니다.
                    <br />
                    현재 URL: <code>/app/test/interim-report/{projectId}</code>
                </p>
            </div>
            <ReportPreview reportData={reportData} />
        </div>
    );
};

export default InterimReportTestPage;
