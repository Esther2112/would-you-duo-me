package site.woulduduo.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QBoard is a Querydsl query type for Board
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QBoard extends EntityPathBase<Board> {

    private static final long serialVersionUID = 1371900338L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QBoard board = new QBoard("board");

    public final EnumPath<site.woulduduo.enumeration.BoardCategory> boardCategory = createEnum("boardCategory", site.woulduduo.enumeration.BoardCategory.class);

    public final StringPath boardContent = createString("boardContent");

    public final NumberPath<Integer> boardLike = createNumber("boardLike", Integer.class);

    public final NumberPath<Long> boardNo = createNumber("boardNo", Long.class);

    public final StringPath boardTitle = createString("boardTitle");

    public final NumberPath<Integer> boardViewCount = createNumber("boardViewCount", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> boardWrittenDate = createDateTime("boardWrittenDate", java.time.LocalDateTime.class);

    public final ListPath<Reply, QReply> replyList = this.<Reply, QReply>createList("replyList", Reply.class, QReply.class, PathInits.DIRECT2);

    public final QUser user;

    public QBoard(String variable) {
        this(Board.class, forVariable(variable), INITS);
    }

    public QBoard(Path<? extends Board> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QBoard(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QBoard(PathMetadata metadata, PathInits inits) {
        this(Board.class, metadata, inits);
    }

    public QBoard(Class<? extends Board> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.user = inits.isInitialized("user") ? new QUser(forProperty("user")) : null;
    }

}

