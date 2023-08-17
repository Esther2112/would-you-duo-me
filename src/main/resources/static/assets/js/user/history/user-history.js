import {drawPieGraph} from "./draw-pie-graph.js";
import {renderReview} from "../../review/render-review.js";
import {scrollPaging} from "../../common/scroll-paging.js";
import {followEventHandler} from "./follow-event-handler.js";
import {makeChattingRoom} from "../../chatting/chatting-modal.js";

(() => {
    // 페이지 번호를 위한 클로저
    const increasePageNo = (() => {
        let pageNo = 0;
        return () => ++pageNo;
    })();

    const userAccount = document.getElementById('history-wrapper').dataset.userAccount;

    // 승률 그래프 그리기
    drawPieGraph(+document.getElementById('numeric-win-rate').dataset.winRate, '#win-rate-graph');

    // 리뷰 1페이지 불러오기
    const $reviewWrapper = document.getElementById('review-wrapper');
    renderReview({
        $wrapper: $reviewWrapper,
        userAccount: userAccount,
        pageNo: increasePageNo
    }).then();

    // 스크롤 이벤트 등록
    const $reviewSection = document.getElementById('review-section');

    scrollPaging($reviewSection, renderReview, {
        $wrapper: $reviewWrapper,
        userAccount: userAccount,
        pageNo: increasePageNo
    });


    // 로그인 했을때만 실행되는 함수
    if (document.getElementById('chatting-btn')) {
        // 채팅 버튼 이벤트 등록
        makeChattingRoom();

        // 팔로우 이벤트 등록
        followEventHandler(userAccount, document.getElementById('user-follow-heart'));
    }

    // 전적 갱신 버튼 이벤트 등록
    document.getElementById('refresh-btn').onclick = async e => {
        const userAccount = e.target.closest('#history-wrapper').dataset.userAccount;

        const response = await fetch(`/api/v1/users/refresh/${userAccount}`,
            {
                method: 'PATCH'
            }
        );

        const result = await response.json();

        if (result) {
            window.location.reload();
        }
    };

})();