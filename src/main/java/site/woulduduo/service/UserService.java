package site.woulduduo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.WebUtils;
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
import site.woulduduo.dto.response.page.PageResponseDTO;
import site.woulduduo.dto.response.user.*;
import site.woulduduo.dto.riot.LeagueV4DTO;
import site.woulduduo.dto.riot.MatchV5DTO;
import site.woulduduo.dto.riot.MostChampInfo;
import site.woulduduo.entity.*;
import site.woulduduo.enumeration.*;
import site.woulduduo.exception.NotFollowedException;
import site.woulduduo.exception.NoRankException;
import site.woulduduo.exception.NotFoundUserException;
import site.woulduduo.repository.*;
import site.woulduduo.util.LoginUtil;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static site.woulduduo.enumeration.LoginResult.*;
import static site.woulduduo.util.LoginUtil.LOGIN_KEY;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserQueryDSLRepositoryCustom userQueryDSLRepositoryCustom;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final AccuseRepository accuseRepository;
    private final ReplyRepository replyRepository;
    private final PasswordEncoder passwordEncoder;
    private final RiotApiService riotApiService;
    private final MatchingRepository matchingRepository;
    private final FollowRepository followRepository;
    private final UserProfileRepository userProfileRepository;
    private final MostChampRepository mostChampRepository;
    private final RecentMatchRepository recentMatchRepository;

    // 회원가입
    public void register(UserRegisterRequestDTO dto) {

        // 이메일 중복 검사
        if (userRepository.countByUserEmail(dto.getUserEmail()) > 0) {
            throw new IllegalArgumentException("이미 등록된 이메일입니다.");
        }

        // 닉네임 중복 검사
        if (userRepository.countByUserNickname(dto.getUserNickname()) > 0) {
            throw new IllegalArgumentException("이미 등록된 닉네임입니다.");
        }

        // 소환사 아이디 중복 검사
        if (userRepository.countByLolNickname(dto.getLolNickname()) > 0) {
            throw new IllegalArgumentException("이미 등록된 롤 닉네임입니다.");
        }

        String lolNickname = dto.getLolNickname();

        // 랭크 정보 가져오기
        LeagueV4DTO rankInfo = null;
        try {
            rankInfo = riotApiService.getRankInfo(lolNickname, "RANKED_SOLO_5x5");
        } catch (NoRankException e) {
            try {
                rankInfo = riotApiService.getRankInfo(lolNickname, "RANKED_FLEX_SR");
            } catch (NoRankException ex) {
                rankInfo = LeagueV4DTO.builder()
                        .tier("UNR")
                        .rank("")
                        .build();
            }
        }

        log.info("lol rank : {}", rankInfo.getRank());

        // 회원 정보 저장
        User user = User.builder()
                .userAccount(dto.getUserEmail())
                .userNickname(dto.getUserNickname())
                .userPassword(passwordEncoder.encode(dto.getUserPassword()))
                .userBirthday(dto.getUserBirthday())
                .userInstagram(dto.getUserInstagram().isEmpty() ? null : dto.getUserInstagram())
                .userTwitter(dto.getUserTwitter().isEmpty() ? null : dto.getUserTwitter())
                .userFacebook(dto.getUserFacebook().isEmpty() ? null : dto.getUserFacebook())
                .lolNickname(lolNickname)
                .userGender(dto.getUserGender() == Gender.M ? Gender.M : Gender.F)
                .lolTier(rankInfo.getTierEnum())
                .lolRank(rankInfo.getRank())
                .lolLeaguePoints(rankInfo.getLeaguePoints())
                .lolTotalWinCount(rankInfo.getWins())
                .lolTotalLoseCount(rankInfo.getLosses())
                .lolWinRate((int) Math.round(rankInfo.getWinRate() * 100))
                .userRecentLoginDate(LocalDateTime.now())
                .build();

        userRepository.save(user);

        // 프로필 사진 저장
        String[] profileImagePaths = dto.getProfileImagePaths();
        if (profileImagePaths != null) {
            for (String imagePath : profileImagePaths) {
                if (imagePath != null) {
                    UserProfile userProfile = UserProfile.builder()
                            .user(user)
                            .profileImage(imagePath)
                            .build();
                    userProfileRepository.save(userProfile);
                }
            }
        }

        log.info("회원 가입이 완료되었습니다.");
    }

    // 중복검사 서비스 처리
    public int checkSignUpValue(String type, String keyword) {
        int flagNum;

        switch (type) {
            case "email":
                flagNum = userRepository.countByUserEmail(keyword);
                break;
            case "nickname":
                flagNum = userRepository.countByUserNickname(keyword);
                break;
            case "lolNickname":
                flagNum = userRepository.countByLolNickname(keyword);
                break;
            default:
                throw new IllegalArgumentException("잘못된 검사 타입입니다.");
        }

        return flagNum;
    }

    // 로그인 검증
    public LoginResult authenticate(LoginRequestDTO dto,
                                    HttpSession session,
                                    HttpServletResponse response) {

        User foundUser = userRepository.findByUserAccount(dto.getUserAccount());

        // 회원가입 여부 확인
        if (foundUser == null) {
            log.info("{} - 회원가입 안함", dto.getUserAccount());
            return NO_ACC;
        }
        // 비밀번호 일치 확인
        if (!passwordEncoder.matches(dto.getUserPassword(), foundUser.getUserPassword())) {
            log.info("비밀번호 불일치");
            return NO_PW;
        }

        // 자동로그인 체크 여부 확인
        if (dto.isAutoLogin()) {
            // 쿠키 생성 - 쿠키값에 세션아이디를 저장
            Cookie autoLoginCookie
                    = new Cookie(LoginUtil.AUTO_LOGIN_COOKIE, session.getId());

            // 쿠키 셋팅 - 수명이랑 사용경로
            int limitTime = 60 * 60 * 24 * 90;
            autoLoginCookie.setMaxAge(limitTime);
            autoLoginCookie.setPath("/"); // 전체 경로

            // 쿠키를 클라이언트에 응답전송
            response.addCookie(autoLoginCookie);

            // DB에도 쿠키에 저장된 값과 수명을 저장
            userRepository.saveAutoLogin(
                    session.getId(),
                    LocalDateTime.now().plusSeconds(limitTime),
                    dto.getUserAccount()
            );
        }

        log.info("{}님 로그인 성공!", foundUser.getUserNickname());
        return SUCCESS;

    }

    public void maintainLoginState(HttpSession session, String userAccount) {
        // 현재 로그인한 사람의 모든 정보
        User user = getUser(userAccount);

        // 현재 로그인한 사람의 화면에 보여줄 일부정보(더 추가해야할수도 있음)
        String lolNickname = user.getLolNickname();
        LoginUserResponseDTO dto = LoginUserResponseDTO.builder()
                .userAccount(user.getUserAccount())
                .userNickname(user.getUserNickname())
                .lolNickname(lolNickname)
                .userCurrentPoint(user.getUserCurrentPoint())
                .userComment(user.getUserComment() != null ? user.getUserComment() : "")
                .userPosition(user.getUserPosition() != null ? user.getUserPosition() : Position.NONE)
                .userMatchingPoint(user.getUserMatchingPoint() != null ? user.getUserMatchingPoint() : 0)
                .userProfileImage(user.getLatestProfileImage())
                .role(user.getRole())
                .userInstagram(user.getUserInstagram())
                .userFacebook(user.getUserFacebook())
                .userTwitter(user.getUserTwitter())
                .userBirthday(user.getUserBirthday())
                .build();

        // userProfileImage 값 확인
        String userProfileImage = dto.getUserProfileImage();
        System.out.println("UserProfileImage: " + userProfileImage);

        // 이 정보를 세션에 저장
        session.setAttribute(LOGIN_KEY, dto);

        // 세션의 수명을 설정
        session.setMaxInactiveInterval(60 * 60); // 1시간

//        // 티어 정보 갱신
//        user.setLolTier(riotApiService.getTier(lolNickname));

//        List<String> newMost3 = riotApiService.getMost3Champions(lolNickname);
//
//        for (int i = 0; i < 3; i++) {
//
//            MostChamp foundMost = mostChampRepository.getAllByUser_UserAccountAndMostNo(userAccount, i + 1);
//
//            try {
//                foundMost.setChampName(newMost3.get(i));
//            } catch (IndexOutOfBoundsException e) {
//                foundMost.setChampName("");
//            }
//
//            mostChampRepository.save(foundMost);
//        }

    }

    // 유저 정보를 가져오는 서비스기능
    public User getUser(String userAccount) {
        return userRepository.findByUserAccount(userAccount);
    }

    public void autoLoginClear(HttpServletRequest request, HttpServletResponse response) {

        // 자동로그인 쿠키를 가져온다
        Cookie c = WebUtils.getCookie(request, LoginUtil.AUTO_LOGIN_COOKIE);

        // 쿠키를 삭제 -> 쿠키의 수명을 0초로 만들어서 다시 클라이언트에게 응답
        if (c != null) {
            c.setMaxAge(0);
            c.setPath("/");
            response.addCookie(c);

            // 데이터베이스에도 자동로그인을 해제한다.
            userRepository.saveAutoLogin(
                    "none",
                    LocalDateTime.now(),
                    LoginUtil.getCurrentLoginMemberAccount(request.getSession())
            );

        }
    }

    // 마이페이지 - 정보수정
    public boolean modifyUser(UserModifyRequestDTO dto) {
        // 사용자 정보 가져오기
        User user = userRepository.findByUserAccount(dto.getUserAccount());
        if (user == null) {
            // 사용자 정보가 존재하지 않는 경우
            return false;
        }

        // 사용자 정보 수정
        user.setUserNickname(dto.getUserNickname());
        user.setUserBirthday(dto.getUserBirthday());
        user.setLolNickname(dto.getLolNickname());
        user.setUserInstagram(dto.getUserInstagram().equals("") ? null : dto.getUserInstagram());
        user.setUserFacebook(dto.getUserFacebook().equals("") ? null : dto.getUserFacebook());
        user.setUserTwitter(dto.getUserTwitter().equals("") ? null : dto.getUserTwitter());

        // 사용자 정보 저장
        userRepository.save(user);

        return true;
    }

    // 마이페이지 - 비밀번호 변경
    public boolean changePassword(HttpSession session, ChangePasswordRequestDTO dto) {
        // 세션에서 현재 사용자 정보를 가져옵니다.
        String userAccount = (String) session.getAttribute("userAccount");
        if (userAccount == null) {
            return false; // 세션에 사용자 계정 정보가 없으면 변경 실패로 처리합니다.
        }

        // 사용자 정보를 데이터베이스에서 조회합니다.
        User user = userRepository.findByUserAccount(userAccount);
        if (user == null) {
            return false; // 사용자 정보가 없으면 변경 실패로 처리합니다.
        }

        // 기존 비밀번호를 확인합니다.
        if (!passwordEncoder.matches(dto.getUserPassword(), user.getUserPassword())) {
            return false; // 입력된 기존 비밀번호가 일치하지 않으면 변경 실패로 처리
        }

        // 새로운 비밀번호를 설정
        String newEncryptedPassword = passwordEncoder.encode(dto.getNewPassword());
        user.setUserPassword(newEncryptedPassword);

        // 사용자 정보를 저장
        userRepository.save(user);

        // 변경된 비밀번호를 세션에 업데이트
        session.setAttribute("userPassword", newEncryptedPassword);

        return true; // 비밀번호 변경 성공을 반환
    }


    /**
     * 마이페이지 - 프로필카드 등록 메서드
     * <p>
     * //     * @param session - 접속한 사용자
     *
     * @param session - 접속한 사용자
     * @return 등록성공여부
     * @Param dto - 프로필카드 등록 dto
     */
    public boolean registerDUO(HttpSession session, UserCommentRequestDTO dto) {

//        User exUser = User.builder()
//                .userSessionId("abc1234@ddd.com")
//                .userAccount("abc1234@ddd.com")
//                .userPassword("12345678")
//                .lolTier(Tier.CHA)
//                .userGender(Gender.M)
//                .userBirthday(LocalDate.of(2000, 03, 16))
//                .userNickname("HongChaa")
//                .lolNickname("HongChaa")
//                .build();
//
//        userRepository.save(exUser);
        LoginUserResponseDTO loginUser = (LoginUserResponseDTO) session.getAttribute("login");
        Optional<User> user = userRepository.findById(loginUser.getUserAccount());

        System.out.println("user = " + user);
        if (user.isEmpty()) {
            return false;
        }

        List<MatchV5DTO.MatchInfo.ParticipantDTO> last20ParticipantDTOList = riotApiService.getLast20ParticipantDTOList(user.get().getLolNickname());

        user.ifPresent(u -> {
            u.setUserPosition(dto.getUserPosition());
            u.setUserComment(dto.getUserComment());
            u.setUserMatchingPoint(dto.getUserMatchingPoint());

            userRepository.save(u);

            // 20게임 정보 저장
            saveHistoryInfo(last20ParticipantDTOList, u);

        });

        return true;
    }

    // 프로필 카드 수정
    public Long modifyUserComment(UserCommentRequestDTO dto, HttpSession session) {
        log.info("dto : {}", dto);

        return userQueryDSLRepositoryCustom.modifyProfileCard(dto, session);
    }

    // 프로필 카드 삭제
    public Long deleteUserComment(UserCommentRequestDTO dto, HttpSession session) {
        log.info("dto : {}", dto);

        return userQueryDSLRepositoryCustom.deleteProfileCard(dto, session);
    }


    public AdminPageResponseDTO getAdminPageInfo() {
        int userFindAllCount = userFindAllCount();
        int userFindByToday = userFindByToday();
        int accuseFindAllCount = accuseFindAllCount();
        int accuseFindByToday = accuseFindByToday();
        int boardFindAllCount = boardFindAllCount();
        int boardFindByToday = boardFindByToday();

        AdminPageResponseDTO build = AdminPageResponseDTO.builder()
                .todayBoardCount(boardFindByToday)
                .todayJoinCount(userFindByToday)
                .todayAccuseCount(accuseFindByToday)
                .totalAccuseCount(accuseFindAllCount)
                .totalBoardCount(boardFindAllCount)
                .totalJoinCount(userFindAllCount)
                .build();

        return build;

    }

    //전체 user 조회수(admin)
    public int userFindAllCount() {
        List<User> all = userRepository.findAll();
        int userSize = all.size();
        return userSize;
    }

    //오늘 가입한 회원 수(admin)
    public int userFindByToday() {
        int allWithJoinDate = userRepository.findAllWithJoinDate(LocalDate.now());
        return allWithJoinDate;
    }

    //전체 accuse 조회수(admin)
    public int accuseFindAllCount() {
        List<Accuse> all = accuseRepository.findAll();
        int accuseSize = all.size();
        return accuseSize;
    }

    //오늘 accuse 조회수(admin)
    public int accuseFindByToday() {
        int allWithAccuseWrittenDate = accuseRepository.findAllWithAccuseWrittenDate();
        System.out.println("allWithAccuseWrittenDate = " + allWithAccuseWrittenDate);
        return allWithAccuseWrittenDate;
    }

    //전체 게시글 조회수(admin)
    public int boardFindAllCount() {
        List<Board> all = boardRepository.findAll();
        int boardsize = all.size();
        return boardsize;
    }

    //오늘 작성된 게시글 조회수(admin)
    public int boardFindByToday() {
        int allWithJoinDate = boardRepository.findAllWithBoardWrittenDate();
        return allWithJoinDate;
    }

    //유저리스트 DTO변환(Admin) + 페이징
    public ListResponseDTO<UserByAdminResponseDTO, User> getUserListByAdmin(PageDTO dto) {

        Pageable pageable = PageRequest.of(
                dto.getPage() - 1,
                dto.getSize(),
                Sort.by("userJoinDate").descending()
        );
        String keyword = dto.getKeyword();

        Page<User> users;

        if (keyword == null || keyword.isEmpty()) {
            // 전체 불러오기
            users = userRepository.findAll(pageable);
            List<User> content = users.getContent();
            int accusesize = content.size();
        } else {
            // 특정 키워드를 포함하는 계정 불러오기
            users = userRepository.findByUserNicknameContaining(keyword, pageable);
            List<User> content = users.getContent();
        }

        List<UserByAdminResponseDTO> collect = users.stream()
                .map(UserByAdminResponseDTO::new)
                .collect(toList());

        int i = (dto.getPage() - 1) * dto.getSize() + 1;
        for (UserByAdminResponseDTO user : collect) {
            user.setRowNum(i);
            i++;
        }

        return ListResponseDTO.<UserByAdminResponseDTO, User>builder()
                .count(collect.size())
                .pageInfo(new PageResponseDTO<>(users))
                .list(collect)
                .build();
    }


    //금일 가입자(Admin)
    public ListResponseDTO<UserByAdminResponseDTO, User> todayUserByAdMin(PageDTO dto) {
        Pageable pageable = PageRequest.of(
                dto.getPage() - 1,
                dto.getSize(),
                Sort.by("userJoinDate").descending()
        );
        String keyword = dto.getKeyword();
        if ("".equals(keyword)) {
            keyword = null;
        }

        String userAccount = dto.getKeyword();
        Page<User> users;

        if (userAccount == null) {
            // 전체 불러오기
            users = userRepository.findAll(pageable);
            int accusesize = users.getContent().size();
            System.out.println("accusesize = " + accusesize);

        } else {
            // 특정 키워드를 포함하는 계정 불러오기
            users = userRepository.findByUserNicknameContaining(userAccount, pageable);
        }

        List<User> todayUserList = new ArrayList<>();
        LocalDate currentDate = LocalDate.now();

        for (User user : users) {
            LocalDate joinDate = user.getUserJoinDate();
            if (joinDate != null && joinDate.equals(currentDate)) {
                todayUserList.add(user);
            }
        }

        System.out.println("todayUserList = " + todayUserList);
        int todaysize = todayUserList.size();
        System.out.println("todaysize = " + todaysize);
        List<UserByAdminResponseDTO> collect = todayUserList.stream()
                .map(UserByAdminResponseDTO::new)
                .collect(toList());

        int i = (dto.getPage() - 1) * dto.getSize() + 1;
        for (UserByAdminResponseDTO user : collect) {
            user.setRowNum(i);
            i++;
        }

        return ListResponseDTO.<UserByAdminResponseDTO, User>builder()
                .count(collect.size())
                .pageInfo(new PageResponseDTO<>(users))
                .list(collect)
                .build();
    }


    //userDetailByAdmin
    public UserDetailByAdminResponseDTO getUserDetailByAdmin(String userNickName) {
        User byUserNickName = userRepository.findByUserNickname(userNickName);
        UserDetailByAdminResponseDTO userDetail =
                new UserDetailByAdminResponseDTO(byUserNickName);


        return userDetail;
    }

    public boolean getUserBanBooleanByAdmin(String userNickname) {
        User byUserNickName = userRepository.findByUserNickname(userNickname);

        boolean userIsBanned = byUserNickName.isUserIsBanned();


        return userIsBanned;
    }


    //포인트 증가
    public boolean increaseUserPoint(UserModifyRequestDTO dto) {
        User byUserNickName = userRepository.findByUserNickname(dto.getUserNickname());

        System.out.println("userByNickName123 = " + byUserNickName);
        //지급포인트
        int userAddPoint = dto.getUserAddPoint();
        System.out.println("userAddPoint = " + userAddPoint);
        String addPoint = String.valueOf(userAddPoint);

        //-99999 ~ +99999 가 맞는지 확인
        boolean matches = addPoint.matches("-?[0-9]{1,5}");
        System.out.println("matches = " + matches);
        //현재포인트
        int userCurrentPoint = byUserNickName.getUserCurrentPoint();
        System.out.println("userCurrentPoint = " + userCurrentPoint);
        //더한값
        int total = userCurrentPoint + userAddPoint;

        //-99999 ~ +99999 가 맞는지 확인
        //현재포인트와 total이 같지 않다면 저장
        if (userCurrentPoint != total) {

            if (matches != false) {
                byUserNickName.setUserCurrentPoint(total);
                User save = userRepository.save(byUserNickName);
                currentPoint(dto);
                System.out.println("save = " + save);
                return true;
            }
            return false;
        }
        return false;

    }

    //현재포인트 렌더링 메서드
    public int currentPoint(UserModifyRequestDTO dto) {
        User byUserNickName = userRepository.findByUserNickname(dto.getUserNickname());
        Integer userCurrentPoint = byUserNickName.getUserCurrentPoint();

        return userCurrentPoint;
    }

    //밴 boolean
    public boolean changeBanStatus(UserModifyRequestDTO dto) {
        int userIsBanned = dto.getUserIsBanned();
        System.out.println("userIsBanned12 = " + userIsBanned);
        User byUserNickName = userRepository.findByUserNickname(dto.getUserNickname());
        boolean userIsBanned1 = byUserNickName.isUserIsBanned();
        System.out.println("userIsBanned1 = " + userIsBanned1);

        //클릭이 동작된것 front 에서 1을 보내줄것
        if (userIsBanned == 1) {
            //userIsBanned가 1이면 참
            if (userIsBanned1 == true) {
                byUserNickName.setUserIsBanned(false);
                User save = userRepository.save(byUserNickName);
                boolean userIsBanned2 = save.isUserIsBanned();
                System.out.println("userIsBanned2 = " + userIsBanned2);
                return false;
            }
            byUserNickName.setUserIsBanned(true);
            User save = userRepository.save(byUserNickName);
            boolean userIsBanned2 = save.isUserIsBanned();
            System.out.println("userIsBanned2 = " + userIsBanned2);
            return true;
        }


        return false;

    }

    //닉네임으로 user 찾기
    public User findUserByNickName(UserModifyRequestDTO dto) {
        User byUserNickName = userRepository.findByUserNickname(dto.getUserNickname());


        return byUserNickName;
    }


    /**
     * 사용자의 듀오 정보를 구하는 메서드
     *
     * @param session     - 접속한 사용자
     * @param userAccount - 대상 사용자
     * @return - 응답 DTO
     */
    public UserHistoryResponseDTO getUserHistoryInfo(HttpSession session, String userAccount) {

        User foundUser = userRepository.findById(userAccount).orElseThrow(
                () -> new NotFoundUserException("해당하는 유저가 없습니다.")
        );

        // 듀오 프로필을 등록하지 않았다면 오류 페이지로 이동
        if (foundUser.getUserComment() == null)
            throw new NotFoundUserException("해당하는 유저가 없습니다.");

        boolean isFollowed = followRepository.existsByFollowFromAndFollowTo(((LoginUserResponseDTO) session.getAttribute(LOGIN_KEY)).getUserAccount(), userAccount) == 1;

        return UserHistoryResponseDTO.builder()
                .userAccount(userAccount)
                .profileImage(foundUser.getLatestProfileImage())
                .userNickname(foundUser.getUserNickname())
                .userPosition(foundUser.getUserPosition())
                .isFollowed(isFollowed)
                .userAvgRate(String.format("%.2f", foundUser.getUserAvgRate()))
                .userMatchingPoint(foundUser.getUserMatchingPoint())
                .userInstagram(foundUser.getUserInstagram())
                .userFacebook(foundUser.getUserFacebook())
                .userTwitter(foundUser.getUserTwitter())
                .lolNickname(foundUser.getLolNickname())
                .userComment(foundUser.getUserComment())
                .tier(foundUser.getLolTier())
                .rank(foundUser.getLolRank())
                // 모스트 3 챔피언 정보
                .mostChampInfos(foundUser.getMostChampList().stream()
                        .map(m -> {
                            int champWinCount = m.getChampWinCount();
                            int champLoseCount = m.getChampLoseCount();
                            return MostChampInfo.builder()
                                    .champName(m.getChampName())
                                    .winCount(champWinCount)
                                    .loseCount(champLoseCount)
                                    .winRate(champWinCount / (champWinCount + champLoseCount) * 100)
                                    .kda(String.format("%.2f", m.getChampKda()))
                                    .build();
                        }).collect(toList()))
                // riot api 를 통해 얻어오는 솔로랭크 혹은 자유랭크 데이터
                .leaguePoints(foundUser.getLolLeaguePoints())
                .totalWinCount(foundUser.getLolTotalWinCount())
                .totalLoseCount(foundUser.getLolTotalLoseCount())
                .winRate(foundUser.getLolWinRate())
                // 최근 20 매치의 정보 데이터
                .last20Matches(foundUser.getRecentMatchList().stream()
                        .map(m -> MatchResponseDTO.builder()
                                .assists(m.getAssists())
                                .championName(m.getChampionName())
                                .deaths(m.getDeaths())
                                .item0(m.getItem0())
                                .item1(m.getItem1())
                                .item2(m.getItem2())
                                .item3(m.getItem3())
                                .item4(m.getItem4())
                                .item5(m.getItem5())
                                .item6(m.getItem6())
                                .kills(m.getKills())
                                .mainPerk(m.getMainPerk())
                                .subPerk(m.getSubPerk())
                                .summoner1Id(m.getSummoner1Id())
                                .summoner2Id(m.getSummoner2Id())
                                .win(m.isWin())
                                .avgKda(m.getAvgKda())
                                .build())
                        .collect(toList()))
                .build();

    }

    public List<UserProfileResponseDTO> getUserProfileList(HttpSession session, UserSearchType userSearchType) {
        List<UserProfileResponseDTO> userProfileList = userQueryDSLRepositoryCustom.getUserProfileList(userSearchType, session);

        for (UserProfileResponseDTO userProfile : userProfileList) {
            log.info("@@@ userProfile @@@@@ : {}", userProfile.toString());
        }
        return userProfileList;
    }


    /**
     * 해당 유저가 현재 팔로우를 했다면 언팔로우, 언팔 상태면 팔로우를 하는 메서드
     *
     * @param followTo - 누구에게
     * @param session  - 로그인 한 사람
     * @return - 팔로우 했다면 true, 언팔로우 했다면 false
     * @throws RuntimeException - 잘못된 유저 정보일 때 예외 던짐
     */
    public boolean follow(String followTo, HttpSession session) throws RuntimeException {

        String followFrom = ((LoginUserResponseDTO) session.getAttribute(LOGIN_KEY)).getUserAccount();

        if (followTo.equals(followFrom)) throw new RuntimeException("자기 자신은 팔로우할 수 없습니다.");

        Follow followState;

        try {
            followState = followRepository.findById(FollowCompositeKey.builder()
                    .followFrom(followFrom)
                    .followTo(followTo)
                    .build()).orElseThrow(
                    () -> new NotFollowedException("팔로우 합니다")
            );
        } catch (NotFollowedException e) {
            followRepository.save(
                    Follow.builder()
                            .followTo(userRepository.findById(followTo).orElseThrow(
                                    () -> new RuntimeException("해당하는 유저가 없습니다.")
                            ))
                            .followFrom(userRepository.findById(followFrom).orElseThrow(
                                    () -> new RuntimeException("해당하는 유저가 없습니다.")
                            ))
                            .build()
            );

            return true;
        }

        followRepository.delete(followState);

        return false;
    }

    //마이페이지 내활동정보 페이지
    public MatchingInfoResponseDTO getMyMypageInfo(String userAccount) {
        User user = userRepository.findById(userAccount).orElseThrow();

//        듀오활동정보
        //남자에게 받은 매칭 요청
        int matchingFromMale = matchingRepository.countByChatting_ChattingFrom_UserGenderAndChatting_ChattingTo(Gender.M, user);
        //여자에게 받은 매칭 요청
        int matchingFromFemale = matchingRepository.countByChatting_ChattingFrom_UserGenderAndChatting_ChattingTo(Gender.F, user);
        //남자에게 받은 매칭 수락건
        List<MatchingStatus> matchingStatus = List.of(MatchingStatus.CONFIRM, MatchingStatus.DONE);
        int confirmWithMale = matchingRepository.countByMatchingStatusInAndChatting_ChattingFrom_UserGenderAndChatting_ChattingTo(matchingStatus, Gender.M, user);
        //여자에게 받은 매칭 수락건
        int confirmWithFemale = matchingRepository.countByMatchingStatusInAndChatting_ChattingFrom_UserGenderAndChatting_ChattingTo(matchingStatus, Gender.F, user);
        //남자와 매칭확정률
        double confirmRateMale = 0.0;
        try {
            confirmRateMale = Math.round(((double) confirmWithMale / (double) matchingFromMale * 10000.0) / 100.0);
        } catch (ArithmeticException e) {
            confirmRateMale = 0.0;
        }
        //여자와 매칭확정률
        double confirmRateFemale = 0;
        try {
            confirmRateFemale = Math.round(((double) confirmWithFemale / (double) matchingFromFemale * 10000.0) / 100.0);
        } catch (ArithmeticException e) {
            confirmRateMale = 0.0;
        }

//        내 호감도
        //팔로워
        int followers = followRepository.countByFollowTo(user);
        //팔로윙
        int followings = followRepository.countByFollowFrom(user);
        //호감도
        Double userAvgRate = user.getUserAvgRate();
        //팔로워순위
        Integer rank = followRepository.getFollowerRank(userAccount);
        //경고횟수
        long accuseCount = accuseRepository.countByUser(user);
        //총활동점수
        long totalScore = (long) ((followers * 200L) + (userAvgRate * 100) - (accuseCount * 500));

        return MatchingInfoResponseDTO.builder()
                .receivedMatchingFromMale(matchingFromMale)
                .receivedMatchingFromFemale(matchingFromFemale)
                .confirmedMatchingWithMale(confirmWithMale)
                .confirmedMatchingWithFemale(confirmWithFemale)
                .confirmRateWithMale(confirmRateMale)
                .confirmRateWithFemale(confirmRateFemale)
                .userAvgRate(userAvgRate)
                .rank(rank)
                .accuseCount(accuseCount)
                .totalScore(totalScore)
                .build();
    }

    public UserInfoResponseDTO getUserInfo(String userAccount) {

        User user = userRepository.findById(userAccount).orElseThrow();

        return UserInfoResponseDTO.builder()
                .profileImage(user.getLatestProfileImage())
                .userCurrentPoint(user.getUserCurrentPoint())
                .userNickname(user.getUserNickname())
                .lolNickname(user.getLolNickname())
                .build();

    }

    public boolean refreshUserHistory(String userAccount) {

        User user = userRepository.findByUserAccount(userAccount);

        deleteExistingHistoryInfo(userAccount);

        List<MatchV5DTO.MatchInfo.ParticipantDTO> last20ParticipantDTOList = riotApiService.getLast20ParticipantDTOList(user.getLolNickname());

        saveHistoryInfo(last20ParticipantDTOList, user);

        return true;

    }

    private void saveHistoryInfo(List<MatchV5DTO.MatchInfo.ParticipantDTO> last20ParticipantDTOList, User user) {

        // 20게임 정보 저장
        for (int i = 0; i < last20ParticipantDTOList.size(); i++) {
            MatchV5DTO.MatchInfo.ParticipantDTO participantDTO = last20ParticipantDTOList.get(i);

            int kills = participantDTO.getKills();
            int deaths = participantDTO.getDeaths();
            int assists = participantDTO.getAssists();
            String avgKda = null;
            try {
                avgKda = String.format("%.2f", ((double) kills + assists / deaths));
            } catch (ArithmeticException e) {
                avgKda = "P";
            }
            recentMatchRepository.save(RecentMatch.builder()
                    .user(user)
                    .recentNo(i + 1)
                    .championName(participantDTO.getChampionName())
                    .kills(kills)
                    .deaths(deaths)
                    .assists(assists)
                    .item0(participantDTO.getItem0())
                    .item1(participantDTO.getItem1())
                    .item2(participantDTO.getItem2())
                    .item3(participantDTO.getItem3())
                    .item4(participantDTO.getItem4())
                    .item5(participantDTO.getItem5())
                    .item6(participantDTO.getItem6())
                    .mainPerk(participantDTO.getPerks().getStyles()[0].getSelections()[0].getPerk())
                    .subPerk(participantDTO.getPerks().getStyles()[1].getStyle())
                    .summoner1Id(participantDTO.getSummoner1Id())
                    .summoner2Id(participantDTO.getSummoner2Id())
                    .win(participantDTO.isWin())
                    .avgKda(avgKda)
                    .build());
        }

        // 20 게임 미만일 경우
        for (int i = last20ParticipantDTOList.size(); i < 20; i++) {
            recentMatchRepository.save(RecentMatch.builder()
                    .user(user)
                    .recentNo(i + 1)
                    .build());
        }

        // 모스트 챔피언 3개 또는 그 이하 저장
        List<String> most3Champions = riotApiService.getMost3Champions(last20ParticipantDTOList);

        for (int i = 0; i < 3; i++) {
            String champName = null;
            int winCount = 0;
            int loseCount = 0;
            double kda = 0;
            try {
                champName = most3Champions.get(i);

                String finalChampName = champName;
                List<MatchV5DTO.MatchInfo.ParticipantDTO> champMatchInfo = last20ParticipantDTOList.stream()
                        .filter(p -> p.getChampionName().equals(finalChampName))
                        .collect(toList());

                winCount = (int) champMatchInfo.stream()
                        .filter(MatchV5DTO.MatchInfo.ParticipantDTO::isWin).count();

                loseCount = champMatchInfo.size() - winCount;

                // stream을 한번에 돌려서 필요한 정보들을 같이 가져오는 (ex 배열에 담던가 객체를 하나 만들어서) 방법으로
                // 리팩토링 가능
                int kills = champMatchInfo.stream()
                        .mapToInt(MatchV5DTO.MatchInfo.ParticipantDTO::getKills).sum();

                int deaths = champMatchInfo.stream()
                        .mapToInt(MatchV5DTO.MatchInfo.ParticipantDTO::getDeaths).sum();

                int assists = champMatchInfo.stream()
                        .mapToInt(MatchV5DTO.MatchInfo.ParticipantDTO::getAssists).sum();

                kda = (double) (kills + assists) / deaths;

            } catch (IndexOutOfBoundsException e) {
                champName = "";
            }

            mostChampRepository.save(MostChamp.builder()
                    .user(user)
                    .mostNo(i + 1)
                    .champName(champName)
                    .champWinCount(winCount)
                    .champLoseCount(loseCount)
                    .champKda(kda)
                    .build());
        }
    }

    private void deleteExistingHistoryInfo(String userAccount) {
        // 존재하고 있는 데이터를 모두 비워줌
        recentMatchRepository.deleteAllByUserAccount(userAccount);
        mostChampRepository.deleteAllByUserAccount(userAccount);
    }
}
