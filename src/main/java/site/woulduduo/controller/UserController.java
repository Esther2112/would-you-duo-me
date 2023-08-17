package site.woulduduo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.woulduduo.aws.S3Service;
import site.woulduduo.dto.request.login.LoginRequestDTO;
import site.woulduduo.dto.request.page.PageDTO;
import site.woulduduo.dto.request.page.UserSearchType;
import site.woulduduo.dto.request.user.ChangePasswordRequestDTO;
import site.woulduduo.dto.request.user.UserCommentRequestDTO;
import site.woulduduo.dto.request.user.UserModifyRequestDTO;
import site.woulduduo.dto.request.user.UserRegisterRequestDTO;
import site.woulduduo.dto.response.ListResponseDTO;
import site.woulduduo.dto.response.chatting.MatchingInfoResponseDTO;
import site.woulduduo.dto.response.login.LoginUserResponseDTO;
import site.woulduduo.dto.response.user.*;
import site.woulduduo.entity.User;
import site.woulduduo.enumeration.*;
import site.woulduduo.exception.NotFoundUserException;
import site.woulduduo.repository.UserRepository;
import site.woulduduo.service.EmailService;
import site.woulduduo.service.UserService;
import site.woulduduo.util.LoginUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static site.woulduduo.enumeration.LoginResult.SUCCESS;
import static site.woulduduo.util.LoginUtil.*;

@Controller
@Slf4j
@RequiredArgsConstructor
public class UserController {

//    @Value("${file.upload.root-path}")
//    private String rootPath;

    private final UserService userService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final PasswordEncoder passwordEncoder;

    // 메인페이지 - 프로필 카드 불러오기(비동기)
    @GetMapping("/api/v1/users/{page}/{keyword}/{size}/{position}/{gender}/{tier}/{follow}/{sort}")
    public ResponseEntity<?> getUserProfileList(@PathVariable int page, @PathVariable String keyword, @PathVariable int size
            , @PathVariable String position, @PathVariable String gender
            , @PathVariable String tier,@PathVariable String follow ,@PathVariable String sort, HttpSession session, Model model) {


        System.out.println(keyword + position + gender + tier + "  "+ follow +"  "+ sort);

        UserSearchType userSearchType = new UserSearchType();
        userSearchType.setPage(page);
        userSearchType.setSize(size);
        userSearchType.setFollowers(follow);
        if (!keyword.equals("-")) {
            userSearchType.setKeyword(keyword);
        }
        if (!position.equals("all")) {
            userSearchType.setPosition(Position.valueOf(position));
        }
        if (!gender.equals("all")) {
            userSearchType.setGender(Gender.valueOf(gender));
        }
        if (!tier.equals("all")) {
            userSearchType.setTier(Tier.valueOf(tier));
        }
        if (!keyword.equals("all")) {
            userSearchType.setSort(sort);
        }
        LoginUserResponseDTO login = (LoginUserResponseDTO)session.getAttribute("login");

        model.addAttribute("login", login);

        List<UserProfileResponseDTO> userServiceUserProfileList = userService.getUserProfileList(session, userSearchType);
        System.out.println("userServiceUserProfileList = " + userServiceUserProfileList);

        return ResponseEntity.ok().body(userServiceUserProfileList);
    }

    // 회원 가입 양식 요청
    @GetMapping("/user/sign-up")
    public String signUp() {
        log.info("/user/sign-up GET ");
        return "user/sign-up";
    }

    // 회원가입 처리 요청
    @PostMapping("/user/sign-up")
    public String signUp(@Valid UserRegisterRequestDTO dto) {
        log.info("/user/sign-up POST! dto : {}", dto);

        // 프로필 사진 배열 저장 로직
        MultipartFile[] profileImages = dto.getProfileImages();
        String[] savePaths = new String[profileImages.length];

        for (int i = 0; i < profileImages.length; i++) {
            MultipartFile profileImage = profileImages[i];
            if (!profileImage.isEmpty()) {
                // 업로드된 파일을 실제 로컬 저장소에 업로드하는 로직
//                String savePath = FileUtil.uploadFile(profileImage, rootPath);
//                savePaths[i] = savePath;
                String fileName = UUID.randomUUID() + "_" + profileImage.getOriginalFilename();
                try {
                    savePaths[i] = s3Service.uploadToBucket(profileImage.getBytes(), fileName);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // UserRegisterRequestDTO에 프로필 사진 경로 설정
        dto.setProfileImagePaths(savePaths);

        // UserRegisterRequestDTO를 UserService의 회원가입 메서드로 전달하여 저장
        userService.register(dto);

        return "redirect:/";
    }

    // 아이디(이메일), 닉네임, 소환사아이디 중복검사
    @GetMapping("/check")
    @ResponseBody
    public ResponseEntity<Boolean> check(@RequestParam String type, @RequestParam String keyword) {
        boolean isDuplicate;
        switch (type) {
            case "email":
                isDuplicate = userRepository.countByUserEmail(keyword) > 0;
                break;
            case "nickname":
                isDuplicate = userRepository.countByUserNickname(keyword) > 0;
                break;
            case "lolNickname":
                isDuplicate = userRepository.countByLolNickname(keyword) > 0;
                break;
            default:
                throw new IllegalArgumentException("잘못된 검사 타입입니다.");
        }
        return ResponseEntity.ok(isDuplicate); // 중복되지 않은 경우에 true 반환
    }

    // 로그인 검증 요청
    @PostMapping("/user/sign-in")
    public String signIn(LoginRequestDTO dto
            , RedirectAttributes ra
            , HttpServletResponse response
            , HttpServletRequest request
    ) {

        log.info("/user/sign-in POST ! - {}", dto);

        LoginResult result = userService.authenticate(dto, request.getSession(), response);

        // 로그인 성공시
        if (result == SUCCESS) {
            // 서버에서 세션에 로그인 정보를 저장
            userService.maintainLoginState(request.getSession(), dto.getUserAccount());

            return dto.getRequestURI();
        }

        // 1회용으로 쓰고 버릴 데이터
        ra.addAttribute("msg", result);

        // 로그인 실패시
        return "redirect:/";
    }


    // 로그아웃 요청 처리
    @GetMapping("/user/sign-out")
    public String signOut(HttpServletRequest request, HttpServletResponse response) {

        HttpSession session = request.getSession();

        // 로그인 중인지 확인
        if (isLogin(session)) {

            // 자동로그인 상태라면 해제
            if (isAutoLogin(request)) {
                userService.autoLoginClear(request, response);
            }

            // 세션에서 login정보 제거
            session.removeAttribute("login");

            // 세션 초기화
            session.invalidate();
            return "redirect:/";
        }
        return "redirect:/";
    }

    // 마이페이지
    @GetMapping("/user/my-page")
    public String showMyPage(HttpSession session, Model model) {
        log.info("/user/my-page GET");

        String userAccount;
        try {
            userAccount = ((LoginUserResponseDTO) session.getAttribute(LOGIN_KEY)).getUserAccount();
        } catch (NullPointerException e) {
            return "/?msg=NEED_LOGIN";
        }

        MatchingInfoResponseDTO myMypageInfoDto = userService.getMyMypageInfo(userAccount);

        model.addAttribute("me", myMypageInfoDto);

        return "my-page/mypage-myinfo";
    }

    @RequestMapping(value = "/api/v1/users", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<?> modify(@RequestBody UserModifyRequestDTO dto, HttpSession session) {

        // 세션에서 로그인한 사용자 정보 가져오기
        LoginUserResponseDTO loggedInUser = (LoginUserResponseDTO) session.getAttribute(LoginUtil.LOGIN_KEY);
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        // 사용자 정보 수정
        boolean isModified = userService.modifyUser(dto);
        if (isModified) {

            session.removeAttribute(LOGIN_KEY);

            userService.maintainLoginState(session, dto.getUserAccount());

            // 정보 변경 성공
            return ResponseEntity.ok("정보가 성공적으로 변경되었습니다.");
        } else {
            // 정보 변경 실패
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("정보 변경에 실패했습니다.");
        }
    }

    // 마이페이지 - 비밀번호 변경 페이지 열기
    @GetMapping("/user/change-password")
    public String changePassword() {
        return "my-page/pwdchange";
    }

    // 마이페이지 - 비밀번호 변경 처리
    @RequestMapping(value = "/api/v1/users/change-password", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<?> changePassword(HttpSession session, @RequestBody ChangePasswordRequestDTO dto) {
        // 세션을 이용한 인증 및 현재 사용자 확인 로직
        String currentUserAccount = (String) session.getAttribute("userAccount");
        if (currentUserAccount == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 사용자 계정 값 조회
        User foundUser = userRepository.findByUserAccount(currentUserAccount);
        if (foundUser == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // 비밀번호 변경 로직
        boolean success = userService.changePassword(session, dto);
        if (!success) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // 변경된 비밀번호 반환
        return ResponseEntity.ok(foundUser.getUserPassword());
    }


//    // 프로필 사진 등록
//    @PostMapping("/api/v1/users/profile")
//    public ResponseEntity<?> addProfile(ProfileAddRequestDTO dto, HttpSession session) {
//        log.info("profileAddRequestDTO : {}", dto);
//        return ResponseEntity.ok().build();
//    }
//
//    // 프로필 사진 삭제
//    @DeleteMapping("/api/v1/users/profile")
//    public ResponseEntity<?> deleteProfile(ProfileDeleteRequestDTO dto, HttpSession session) {
//        log.info("profileDeleteRequestDTO : {}", dto);
//        return ResponseEntity.ok().build();
//    }


    // 마이페이지 - 프로필 카드 등록페이지 열기
    @GetMapping("/user/register-duo")
    public String registerDUO(HttpSession session, Model model) {

        String userAccount;

        LoginUserResponseDTO loginInfo = ((LoginUserResponseDTO) session.getAttribute(LOGIN_KEY));

        try {
            userAccount = loginInfo.getUserAccount();
            model.addAttribute(LOGIN_KEY, loginInfo);
        } catch (NullPointerException e) {
            return "/?msg=NEED_LOGIN";
        }

        return "my-page/mypage-duoprofile";
    }


    // 마이페이지 - 프로필카드 등록 처리
    @PostMapping("/user/register-duo")
    public String registerDUO(HttpSession session, UserCommentRequestDTO dto, Model model) {

        String userAccount;

        try {
            userAccount = ((LoginUserResponseDTO) session.getAttribute(LOGIN_KEY)).getUserAccount();
        } catch (NullPointerException e) {
            return "/?msg=NEED_LOGIN";
        }

        boolean b = userService.registerDUO(session, dto);
        log.info("프로필카드등록 성공여부 : {}", b);
        log.info("@@@@dto@@@@ :{}", dto);
        if (b) {
            log.info("success register");
            LoginUserResponseDTO login = (LoginUserResponseDTO)session.getAttribute("login");
            login.setUserComment(dto.getUserComment());
            login.setUserPosition(dto.getUserPosition());
            login.setUserMatchingPoint(dto.getUserMatchingPoint());
        } else {
            log.info("failed register");
        }
        return "redirect:/user/register-duo";
    }

    // 마이페이지 - 프로필카드 수정
    @PostMapping("/user/modify-duo")
    public String modifyDUO(UserCommentRequestDTO dto, HttpSession session) {
        Long result = userService.modifyUserComment(dto, session);
        if (result > 0) {
            log.info("success update");
            LoginUserResponseDTO login = (LoginUserResponseDTO) session.getAttribute("login");
            login.setUserComment(dto.getUserComment());
            login.setUserPosition(dto.getUserPosition());
            login.setUserMatchingPoint(dto.getUserMatchingPoint());
        } else {
            log.info("failed update");
        }

        return "redirect:/user/register-duo";
    }

    // 마이페이지 - 프로필카드 삭제
    @PostMapping("/user/delete-duo")
    public String deleteDUO(UserCommentRequestDTO dto, HttpSession session) {
        Long result = userService.deleteUserComment(dto, session);
        if (result > 0) {
            log.info("success delete");
            LoginUserResponseDTO login = (LoginUserResponseDTO) session.getAttribute("login");
            login.setUserComment("");
            login.setUserPosition(Position.NONE);
            login.setUserMatchingPoint(0);
        } else {
            log.info("failed delete");
        }

        return "redirect:/user/register-duo";
    }

    // 마이 페이지 - 쓴 리뷰 페이지 열기
    @GetMapping("/user/matching-list")
    public String showMatchingList(HttpSession session) {

        String userAccount;

        try {
            userAccount = ((LoginUserResponseDTO) session.getAttribute(LOGIN_KEY)).getUserAccount();
        } catch (NullPointerException e) {
            return "/?msg=NEED_LOGIN";
        }

        return "my-page/matching-list";
    }

    @GetMapping("/user/admin")
    //관리자 페이지 열기
    public String showAdminpage(HttpSession session, Model model) {

        String x = isAdmin(session);
        if (x != null) return x;

        AdminPageResponseDTO adminPageInfo = userService.getAdminPageInfo();
        model.addAttribute("count", adminPageInfo);
        return "admin/admin";
    }

    @GetMapping("/user/modal")
//모달테스트
    public String modalTest(/*HttpSession session, */Model model) {

        return "admin/adminModal";
    }

    private static String isAdmin(HttpSession session) {
        try {
            if (!((LoginUserResponseDTO) session.getAttribute(LOGIN_KEY)).getRole().equals(Role.ADMIN)) {
                return "redirect:/?msg=NOT_ADMIN";
            }
        } catch (NullPointerException e) {
            return "redirect:/?msg=NEED_LOGIN";
        }
        return null;
    }

    //관리자 페이지 리스트 가져오기
    @GetMapping("/api/v1/users/admin")
    public ResponseEntity<?> getUserListByAdmin(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int pageNum) {

        if (keyword == null) {
            keyword = ""; // keyword 값이 null일 경우 빈 문자열로 설정
        }

        PageDTO dto = PageDTO.builder()
                .page(pageNum)
                .keyword(keyword)
                .size(10)
                .build();

        // 이하 코드 생략

        log.info("{}ddttoo==", dto);
        ListResponseDTO<UserByAdminResponseDTO, User> userListByAdmin = userService.getUserListByAdmin(dto);
        log.info("userbyadmin11111 : {}", userListByAdmin);

        log.info("/api/v1/users/admin/");

        return ResponseEntity
                .ok()
                .body(userListByAdmin);
    }
    //관리자 페이지 금일 가입자 리스트 가져오기
    @GetMapping("/api/v1/users/admin1")
    public ResponseEntity<?> getTodayUserListByAdmin(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int pageNum) {


        PageDTO dto = PageDTO.builder()
                .page(pageNum)
                .keyword(keyword)
                .size(10)
                .build();

        log.info("dtodto={}", dto);

        ListResponseDTO<UserByAdminResponseDTO, User> userListTodayByAdmin = userService.todayUserByAdMin(dto);
        log.info("userListTodayByAdmin123 : {}", userListTodayByAdmin);

        return ResponseEntity
                .ok()
                .body(userListTodayByAdmin);
    }

    @GetMapping("/user/detail/admin")
    //관리자 페이지 자세히 보기
    public String showDetailByAdmin(HttpSession session, Model model, @RequestParam String userNickname) {
        log.info("{}nickname = ", userNickname);

        UserDetailByAdminResponseDTO userDetailByAdmin = userService.getUserDetailByAdmin(userNickname);

        log.info("{}userDetailByAdmin = ", userDetailByAdmin);

        model.addAttribute("udByAdmin", userDetailByAdmin);
        return "admin/admin_user";

    }

    @GetMapping("/user/detail/banBoolean")
//    //관리자 페이지 자세히 보기
    public ResponseEntity<?> showBanIsBoolean(HttpSession session, @RequestParam String nickname) {
        log.info("{}nicknamedetail = ", nickname);

        boolean userDetailByAdmin = userService.getUserBanBooleanByAdmin(nickname);

        log.info("{}userDetailByAdmin11111  = ", userDetailByAdmin);

        return ResponseEntity
                .ok()
                .body(userDetailByAdmin);

    }

    @PostMapping("/user/point")
    @ResponseBody
    public ResponseEntity<?> changePointStatus(
            HttpSession session, @RequestBody UserModifyRequestDTO dto) {
        log.info("{}-----------------------", dto);
        boolean b = userService.increaseUserPoint(dto);
        log.info("{}---123123", b);
        int i = userService.currentPoint(dto);


        return ResponseEntity
                .ok()
                .body(i);
    }


    @PostMapping("/user/ban")
    @ResponseBody
    public ResponseEntity<?> changeBanStatus(HttpSession session, @RequestBody UserModifyRequestDTO dto) {
        String userNickname = dto.getUserNickname();
        log.info("{}123123123", userNickname);
        log.info("{}---------userIsBanneduserIsBanned---------", dto);
        boolean b = userService.changeBanStatus(dto);
        log.info("{}aaaaaaaaaaaaaaaaaaaa123123", b);

        return ResponseEntity.ok().body(b);
    }


    // 유저 전적 페이지 이동
    @GetMapping("/user/user-history")
    public String showUserHistory(HttpSession session, Model model, String userAccount) {

        log.info("/user/history?userAccount={} GET", userAccount);

        UserHistoryResponseDTO dto = null;
        try {
            dto = userService.getUserHistoryInfo(session, userAccount);
        } catch (NotFoundUserException e) {
            return "error/error444";
        }

        model.addAttribute("history", dto);

        return "user/user-history";

    }

    // 유저 팔로우
    // 이미 팔로우되어 있다면 언팔로우
    @PatchMapping("/api/v1/users/{userAccount}")
    public ResponseEntity<?> follow(
            @PathVariable String userAccount,
            HttpSession session
    ) {

        try {
            boolean isFollowed = userService.follow(userAccount, session);

            return ResponseEntity.ok(isFollowed);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/v1/users/infos")
    public ResponseEntity<?> getUserInfos(HttpSession session) {
        LoginUserResponseDTO loginUser = (LoginUserResponseDTO) session.getAttribute(LOGIN_KEY);

        if (loginUser == null) {
            return ResponseEntity.badRequest().build();
        }

        UserInfoResponseDTO userInfo = userService.getUserInfo(loginUser.getUserAccount());

        return ResponseEntity.ok(userInfo);
    }

    @PatchMapping("/api/v1/users/refresh/{userAccount}")
    public ResponseEntity<?> refreshUserHistory(@PathVariable String userAccount) {
        boolean result = userService.refreshUserHistory(userAccount);

        return  ResponseEntity.ok(result);
    }

}

