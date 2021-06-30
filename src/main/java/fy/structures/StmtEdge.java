package fy.structures;

import org.jgrapht.graph.DefaultWeightedEdge;

public class StmtEdge extends DefaultWeightedEdge {
    private EdgeTypes type;
    private int score;
    private StmtVertex src;
    private StmtVertex tgt;

    public StmtEdge(EdgeTypes type, int score, StmtVertex src, StmtVertex tgt) {
        this.type = type;
        this.score = score;
        this.src = src;
        this.tgt = tgt;
    }

    public StmtEdge(EdgeTypes type, StmtVertex src, StmtVertex tgt) {
        this.type = type;
        this.src = src;
        this.tgt = tgt;
    }

    public EdgeTypes getType() {
        return type;
    }

    public void setType(EdgeTypes type) {
        this.type = type;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public StmtVertex getSrc() {
        return src;
    }

    public void setSrc(StmtVertex src) {
        this.src = src;
    }

    public StmtVertex getTgt() {
        return tgt;
    }

    public void setTgt(StmtVertex tgt) {
        this.tgt = tgt;
    }
}
