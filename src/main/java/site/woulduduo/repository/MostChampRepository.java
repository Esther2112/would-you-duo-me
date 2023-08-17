package site.woulduduo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import site.woulduduo.entity.MostChamp;

import javax.transaction.Transactional;

public interface MostChampRepository extends JpaRepository<MostChamp, Long> {

    MostChamp getAllByUser_UserAccountAndMostNo(String userAccount, int mostNo);

    @Modifying
    @Transactional
    @Query("delete from MostChamp m where m.user.userAccount = :userAccount")
    void deleteAllByUserAccount(String userAccount);

}
