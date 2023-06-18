package site.woulduduo.repository;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.util.StringUtils;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.woulduduo.dto.request.page.UserSearchType;
import site.woulduduo.dto.response.ListResponseDTO;
import site.woulduduo.dto.response.user.UserProfilesResponseDTO;
import site.woulduduo.entity.QUser;
import site.woulduduo.entity.User;
import site.woulduduo.enumeration.Gender;
import site.woulduduo.enumeration.Position;
import site.woulduduo.enumeration.Tier;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserQueryDSLRepositoryImpl implements UserQueryDSLRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    QUser user =  QUser.user;


    @Override
    public List<UserProfilesResponseDTO> getUserProfileList(UserSearchType userSearchType/*, HttpSession session*/) {

        List<User> userList = queryFactory.selectFrom(user)
                .where(keywordContains(userSearchType.getKeyword())
                        , positioneq(userSearchType.getPosition())
                        , gendereq(userSearchType.getGender())
                        , tiereq(userSearchType.getTier())
                        , user.userMatchingPoint.isNotNull()
                )
                .offset(userSearchType.getPage())
                .limit(userSearchType.getSize())
                .orderBy(user.userAvgRate.desc())
                .fetch();

        List<UserProfilesResponseDTO> userProfiles = new ArrayList<>();
        for (User user : userList) {
            UserProfilesResponseDTO dto = UserProfilesResponseDTO.builder()
                    .userAccount(user.getUserAccount())
                    .userGender(user.getUserGender())
                    .userComment(user.getUserComment())
                    .userMatchingPoint(user.getUserMatchingPoint())
                    .tier(user.getLolTier())
                    .userInstagram(user.getUserInstagram())
                    .userFacebook(user.getUserFacebook())
                    .userTwitter(user.getUserTwitter())
//                    .isFollowed(user.get)
                    .userPosition(user.getUserPosition())
                    .userNickname(user.getUserNickname())
                    .avgRate(user.getUserAvgRate())
//                    .profileImage()
                    .build();

            userProfiles.add(dto);
        }

        for (User user : userList) {
        System.out.println("Repository QueryDSL user = " + user);

        }

        return userProfiles;
    }

    // 티어 파라미터가 null인지 체크
    private BooleanExpression tiereq(Tier tier) {
        return (tier != null) ? user.lolTier.eq(tier) : null;
    }


    // 성별 파라미터가 null인지 체크
    private BooleanExpression gendereq(Gender gender) {
        return (gender != null) ? user.userGender.eq(gender) : null;
    }

    // 포지션 파라미터가 null인지 체크
    private BooleanExpression positioneq(Position position) {
        String sPosition = String.valueOf(position);

        return StringUtils.isNullOrEmpty(sPosition)? user.userPosition.eq(position) : null;
    }

    // 검색 키워드가 null인지 체크
    private BooleanExpression keywordContains(String keyword) {
        return StringUtils.isNullOrEmpty(keyword)? null : user.userNickname.contains(keyword);
    }


}
