package old;

import com.soyunju.logcollector.dto.AiAnalysisResult;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import com.soyunju.logcollector.service.ai.OpenAiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ErrorLogAiService {

    private final ErrorLogRepository errorLogRepository;
    private final OpenAiAnalysisService openAiAnalysisService;

    public AiAnalysisResult startAiAnalysis(Long logId) {
        if (!errorLogRepository.existsById(logId)) {
            throw new IllegalArgumentException("존재하지 않는 로그 ID: " + logId);
        }
        return openAiAnalysisService.openAiAnalysis(logId);
    }
}
