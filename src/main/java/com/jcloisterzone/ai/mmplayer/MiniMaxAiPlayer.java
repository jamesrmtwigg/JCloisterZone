package com.jcloisterzone.ai.mmplayer;

import java.util.EnumSet;
import java.util.Set;

import com.jcloisterzone.Expansion;
import com.jcloisterzone.ai.RankingAiPlayer;
import com.jcloisterzone.board.Position;

public class MiniMaxAiPlayer extends RankingAiPlayer
{

	public static EnumSet<Expansion> supportedExpansions() {
        return EnumSet.of(
            Expansion.BASIC
        );
    }
	
	@Override
	protected double rank()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
    public void selectDragonMove(Set<Position> positions, int movesLeft) {
		throw new UnsupportedOperationException("This AI player supports only the basic game.");
	}
}
