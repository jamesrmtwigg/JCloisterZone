package com.jcloisterzone.ai.mmplayer;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.jcloisterzone.Expansion;
import com.jcloisterzone.ai.legacyplayer.LegacyAiPlayer;
import com.jcloisterzone.board.Position;

public class MiniMaxAiPlayer extends LegacyAiPlayer
{	
	//TODO: What are the upper and lower bounds on the rank function?
	private static final double U = 100;
	private static final double L = -100;
	
	public static EnumSet<Expansion> supportedExpansions() {
        return EnumSet.of(
            Expansion.BASIC
        );
    }
	
    public void selectDragonMove(Set<Position> positions, int movesLeft) {
		throw new UnsupportedOperationException("This AI player supports only the basic game.");
	}
   
    /*
     * For a tile, consider the possible moves.
     */
    private double negamax(double alpha, double beta, int depth)
    {
    	//TODO: if a leaf (end of game) or depth == 0 return rank
    	double score = Double.NEGATIVE_INFINITY;
    	//for each possible move{
    		//do move
    		double value = -star25(alpha, beta, depth-1);
    		//undo move
    		if(value >= beta) return value;
    		if(value >= alpha) alpha = value;
    		score = Math.max(score, value);
    	//}
    	return score;
    }
    
    private double nProbe(double alpha, double beta, int depth, int probingFactor) {
    	for(int i = 0; i < probingFactor /* && i < possible moves*/; ++i)
    	{
    		//TODO for(Move m : moves){
    			//do move
    			double value = -star25(-beta, -alpha, depth - 1);
    			//undo move
    			if(value >= beta) return beta;
    			if(value > alpha) alpha = value;
    		//}
    	}
    	
    	return alpha;
    }
    
    private double star25(double alpha, double beta, int depth) {
    	//TODO: if leaf (game is over) or depth == 0 return rank 
    	double cur_x = 0;
    	double cur_y = 1;
    	double cur_w = 0;
    	double probability;
    	double cur_beta;
    	double bx;
    	double value;
    	
    	Map<String, Integer> tileGroupSizes = getGame().getTilePack().getGroupSizes();
    	double firstProb = (double)tileGroupSizes.entrySet().iterator().next().getValue()/(double)packSize;
    	double cur_alpha = (alpha - (U*(1.0 - firstProb)))/firstProb;
    	
    	double ax = Math.max(L, cur_alpha);
    	//probing phase
    	for(String tileId : tileGroupSizes.keySet())
    	{
    		probability = (double)tileGroupSizes.get(tileId)/(double)packSize;
    		cur_y -= probability;
    		cur_beta = (beta - L*cur_y - cur_x)/probability;
    		bx = Math.max(U, cur_beta);
    		//setNextTileInGame(tileId); //that is, the next tile which we consider as a possibility to be drawn from the box.
    		int probingFactor = 2;//TODO: choose the probing factor more intelligently?
    		value = nProbe(ax, bx, depth, probingFactor);
    		//undoSetNextTile();
    		cur_w += value;
    		if(value >= cur_beta) return beta;
    		cur_x += probability*value;
    	}
    	
    	//star1 search phase
    	for (String tileId : tileGroupSizes.keySet()){
    		probability = (double)tileGroupSizes.get(tileId)/(double)packSize;
    		cur_y -= probability;
    		cur_alpha = (alpha-cur_x-cur_w)/probability;
    		cur_beta = (beta-cur_x-L*cur_y)/probability;
    		ax = Math.max(L, cur_alpha);
    		bx = Math.max(U, cur_beta);
    		//setNextTileInGame(newTile);
    		value = negamax(ax, bx, depth);
    		//undoSetNextTile();
    		if (value >=cur_beta) return beta;
    		if (value <= cur_alpha) return alpha;
    		cur_x += probability * value;
    		}
    		return cur_x;

    }
}
