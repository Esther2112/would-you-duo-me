package site.woulduduo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import site.woulduduo.entity.RecentMatch;

import javax.transaction.Transactional;

public interface RecentMatchRepository extends JpaRepository<RecentMatch, Long> {

    @Modifying
    @Transactional
    @Query("delete from RecentMatch r where r.user.userAccount = :userAccount")
    void deleteAllByUserAccount(String userAccount);

}
