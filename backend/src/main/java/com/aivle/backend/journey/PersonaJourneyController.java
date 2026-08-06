package com.aivle.backend.journey;
import com.aivle.backend.common.response.ApiResponse; import com.aivle.backend.common.security.CurrentUserProvider; import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import java.util.List; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v2/projects/{projectId}") @RequiredArgsConstructor
public class PersonaJourneyController {
 private final PersonaJourneyService personas; private final CurrentUserProvider currentUser;
 @PostMapping("/persona-studies") public ApiResponse<PersonaJourneyService.StudyView> createStudy(@PathVariable Long projectId,HttpServletRequest request){return ApiResponse.success(personas.createStudy(user(),projectId),id(request));}
 @GetMapping("/persona-studies/current") public ApiResponse<PersonaJourneyService.StudyView> currentStudy(@PathVariable Long projectId,HttpServletRequest request){return ApiResponse.success(personas.currentStudy(user(),projectId),id(request));}
 @PostMapping("/persona-cards/generate") public ApiResponse<List<PersonaJourneyService.PersonaView>> generate(@PathVariable Long projectId,HttpServletRequest request){return ApiResponse.success(personas.generate(user(),projectId),id(request));}
 @GetMapping("/persona-cards") public ApiResponse<List<PersonaJourneyService.PersonaView>> cards(@PathVariable Long projectId,HttpServletRequest request){return ApiResponse.success(personas.cards(user(),projectId),id(request));}
 @PostMapping("/persona-interviews") public ApiResponse<List<PersonaJourneyService.InterviewView>> interview(@PathVariable Long projectId,@Valid @RequestBody PersonaJourneyService.InterviewRequest body,HttpServletRequest request){return ApiResponse.success(personas.interview(user(),projectId,body),id(request));}
 @GetMapping("/persona-interviews") public ApiResponse<List<PersonaJourneyService.InterviewView>> interviews(@PathVariable Long projectId,HttpServletRequest request){return ApiResponse.success(personas.interviews(user(),projectId),id(request));}
 @PostMapping("/interview-syntheses") public ApiResponse<PersonaJourneyService.SynthesisView> synthesize(@PathVariable Long projectId,HttpServletRequest request){return ApiResponse.success(personas.synthesize(user(),projectId),id(request));}
 @GetMapping("/interview-syntheses/current") public ApiResponse<PersonaJourneyService.SynthesisView> currentSynthesis(@PathVariable Long projectId,HttpServletRequest request){return ApiResponse.success(personas.currentSynthesis(user(),projectId),id(request));}
 private Long user(){return currentUser.currentUserId();} private String id(HttpServletRequest request){return request.getHeader("X-Request-Id");}
}
