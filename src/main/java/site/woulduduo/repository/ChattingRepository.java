package site.woulduduo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.woulduduo.entity.Chatting;
import site.woulduduo.entity.User;

import java.util.List;

public interface ChattingRepository extends JpaRepository<Chatting, Long> {

    List<Chatting> findByChattingFromAndChattingTo(User chattingFrom, User chattingTo);

    Chatting findByChattingNo(long chattingNo);
}
