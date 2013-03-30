package com.jcloisterzone.ai.mmplayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.jcloisterzone.Expansion;
import com.jcloisterzone.Player;
import com.jcloisterzone.ai.PositionRanking;
import com.jcloisterzone.ai.SavePointManager;
import com.jcloisterzone.ai.copy.CopyGamePhase;
import com.jcloisterzone.ai.legacyplayer.LegacyAiPlayer;
import com.jcloisterzone.board.DefaultTilePack;
import com.jcloisterzone.board.Position;
import com.jcloisterzone.board.Tile;
import com.jcloisterzone.event.GameEventAdapter;
import com.jcloisterzone.game.Game;
import com.jcloisterzone.game.Snapshot;
import com.jcloisterzone.game.phase.GameOverPhase;
import com.jcloisterzone.game.phase.Phase;

public class MiniMaxAiPlayer extends LegacyAiPlayer
{	
	//TODO: What are the upper and lower bounds on the rank function?
	private static final double U = 100;
	private static final double L = -100;
	
	private Stack<Game> gameStack = new Stack<Game>();
	private SavePointManager spm;
	private PositionRanking bestSoFar;
	
	public static EnumSet<Expansion> supportedExpansions() {
        return EnumSet.of(
            Expansion.BASIC
        );
    }
	
    public void selectDragonMove(Set<Position> positions, int movesLeft) {
		throw new UnsupportedOperationException("This AI player supports only the basic game.");
	}
   
    
    
    /*public Game getGame()
    {
    	return gameStack.peek();
    }*/
    
    private void backupGame()
    {
    	
        gameStack.push(getGame());

        Snapshot snapshot = new Snapshot(getGame(), 0);
        Game gameCopy = snapshot.asGame();
        gameCopy.setConfig(getGame().getConfig());
        gameCopy.addGameListener(new GameEventAdapter());
        gameCopy.addUserInterface(this);
        Phase phase = new CopyGamePhase(gameCopy, snapshot, getGame().getTilePack());
        gameCopy.getPhases().put(phase.getClass(), phase);
        gameCopy.setPhase(phase);
        phase.startGame();
        setGame(gameCopy);

        spm = new SavePointManager(getGame());
        bestSoFar = new PositionRanking(Double.NEGATIVE_INFINITY);
        spm.startRecording();
    }
    
    private void restoreGame() {
        assert !gameStack.isEmpty();
        spm.stopRecording();
        spm = null;
        setGame(gameStack.pop());
    }
    
    private double rankLeaf()
    {
    	Game game = gameStack.isEmpty() ? getGame() : gameStack.peek();
		Phase gop = game.getPhases().get(GameOverPhase.class); 
		gop.enter();
		Player winner = game.getActivePlayer();
		for(Player p : game.getAllPlayers())
		{
			if(p.getPoints() > winner.getPoints()) winner = p;
		}
		return winner == this.getPlayer() ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
    }
    
    private void setCurrentTile(String tileId)
    {
    	getGame().setCurrentTile(getGame().getTilePack().drawTile(tileId));
    }
    
    private void unsetCurrentTile()
    {
    	Tile t = getGame().getCurrentTile();
    	((DefaultTilePack)getGame().getTilePack()).addTile(t, "default");
    }
    
    private static String[] sortKeysByValueDescending(final Map<String, ArrayList<Tile>> map) {
    	String[] ids = (String[])map.keySet().toArray();
    	Arrays.sort(ids, new Comparator<String>()
		{

			@Override
			public int compare(String o1, String o2)
			{
				return map.get(o2).size() - map.get(o1).size();
			}
    		
		});
    	
    	return ids;
    }
    
    /*
     * For a tile, consider the possible moves.
     */
    private double negamax(double alpha, double beta, int depth)
    {
    	if(packSize < 1)
    	{
    		return rankLeaf();
    	}
    	if(depth == 0)
    	{
    		return rank();
    	}
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
    	if(packSize < 1)
    	{
    		return rankLeaf();
    	}
    	if(depth == 0)
    	{
    		return rank();
    	}
    	
    	double cur_x = 0;
    	double cur_y = 1;
    	double cur_w = 0;
    	double probability;
    	double cur_beta;
    	double bx;
    	double value;
    	
    	Map<String, ArrayList<Tile>> tileTypes = getGame().getTilePack().getTilesRemaining();
    	String[] tileIDs = sortKeysByValueDescending(tileTypes);
    	double firstProb = (double)tileTypes.get(tileIDs[0]).size()/(double)packSize; 
    	double cur_alpha = (alpha - (U*(1.0 - firstProb)))/firstProb;
    	
    	double ax = Math.max(L, cur_alpha);
    	//probing phase
    	for(String tileId : tileIDs)
    	{
    		if(tileTypes.get(tileId).isEmpty()) break;
    		probability = (double)tileTypes.get(tileId).size()/(double)packSize;
    		cur_y -= probability;
    		cur_beta = (beta - L*cur_y - cur_x)/probability;
    		bx = Math.max(U, cur_beta);
    		//the next tile which we consider as a possibility to be drawn from the box.
    		setCurrentTile(tileId);
    		int probingFactor = 2;//TODO: choose the probing factor more intelligently?
    		value = nProbe(ax, bx, depth, probingFactor);
    		unsetCurrentTile();
    		
    		cur_w += value;
    		if(value >= cur_beta) return beta;
    		cur_x += probability*value;
    	}
    	
    	//star1 search phase
    	for (String tileId : tileIDs){
    		if(tileTypes.get(tileId).isEmpty()) break;
    		probability = (double)tileTypes.get(tileId).size()/(double)packSize;
    		cur_y -= probability;
    		cur_alpha = (alpha-cur_x-cur_w)/probability;
    		cur_beta = (beta-cur_x-L*cur_y)/probability;
    		ax = Math.max(L, cur_alpha);
    		bx = Math.max(U, cur_beta);
    		setCurrentTile(tileId);
    		value = negamax(ax, bx, depth);
    		unsetCurrentTile();
    		if (value >=cur_beta) return beta;
    		if (value <= cur_alpha) return alpha;
    		cur_x += probability * value;
    	}
    		return cur_x;

    }
}
