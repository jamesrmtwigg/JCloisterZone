package com.jcloisterzone.ai.mmplayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Map.Entry;
import java.util.TreeSet;

import com.jcloisterzone.Expansion;
import com.jcloisterzone.Player;
import com.jcloisterzone.action.MeepleAction;
import com.jcloisterzone.action.PlayerAction;
import com.jcloisterzone.action.TilePlacementAction;
import com.jcloisterzone.ai.PositionRanking;
import com.jcloisterzone.ai.SavePoint;
import com.jcloisterzone.ai.SavePointManager;
import com.jcloisterzone.ai.copy.CopyGamePhase;
import com.jcloisterzone.ai.legacyplayer.LegacyAiPlayer;
import com.jcloisterzone.board.DefaultTilePack;
import com.jcloisterzone.board.Location;
import com.jcloisterzone.board.Position;
import com.jcloisterzone.board.Rotation;
import com.jcloisterzone.board.Tile;
import com.jcloisterzone.event.GameEventAdapter;
import com.jcloisterzone.figure.Follower;
import com.jcloisterzone.figure.Meeple;
import com.jcloisterzone.figure.SmallFollower;
import com.jcloisterzone.game.Game;
import com.jcloisterzone.game.Snapshot;
import com.jcloisterzone.game.phase.GameOverPhase;
import com.jcloisterzone.game.phase.Phase;

public class MiniMaxAiPlayer extends LegacyAiPlayer
{	
	//TODO: Jtwigg: What are the upper and lower bounds on the rank function?
	private static final double U = 100;
	private static final double L = -100;
	
	private Stack<Game> gameStack = new Stack<Game>();
	private SavePointManager spm;
	private PositionRanking bestSoFar;
	
	private Player currentPlayer = getPlayer();
	
	public static EnumSet<Expansion> supportedExpansions() {
        return EnumSet.of(
            Expansion.BASIC
        );
    }
	
    public void selectDragonMove(Set<Position> positions, int movesLeft) {
		throw new UnsupportedOperationException("This AI player supports only the basic game.");
	}
   
    private void swapCurrentPlayer()
    {
    	Player[] players = getGame().getAllPlayers();
    	currentPlayer = (currentPlayer == players[0]) ? players[1] : players[0];
    	getGame().setTurnPlayer(currentPlayer);
    }
    
    public Game getGame()
    {
    	return gameStack.isEmpty() ? super.getGame() : gameStack.peek();
    }
    
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
    
    private void replaceTile(String tileId)
    {
    	Tile t = new Tile(tileId);
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
    
    private void doMove(PositionRanking move)
    {
    	//TODO: JTwigg: method stub
    }
    
    private void undoMove()
    {
    	//TODO: Jtwigg: method stub. Use a save point manager probably.
    }
    
    protected TreeSet<PositionRanking> rankMoves(Map<Position, Set<Rotation>> placements) {
        //logger.info("---------- Ranking start ---------------");
        //logger.info("Positions: {} ", placements.keySet());
    	TreeSet<PositionRanking> rankedPlacements = new TreeSet<PositionRanking>();
        backupGame();
        SavePoint sp = spm.save();
        for(Entry<Position, Set<Rotation>> entry : placements.entrySet()) {
            Position pos = entry.getKey();
            for(Rotation rot : entry.getValue()) {
                //logger.info("  * phase {} -> {}", getGame().getPhase(), getGame().getPhase().getDefaultNext());
                //logger.info("  * placing {} {}", pos, rot);
                getGame().getPhase().placeTile(rot, pos);
                //logger.info("  * phase {} -> {}", getGame().getPhase(), getGame().getPhase().getDefaultNext());
                
                phaseLoop();
                double currRank = rank();
                rankedPlacements.add(new PositionRanking(currRank, pos, rot));
                if(currentPlayer.hasFollower())
                {
                	Set<Location> locs = getGame().getBoard().get(pos).getUnoccupiedScoreables(false); 
                	for(Location loc : locs)
                	{
                		SavePoint beforeMeepleSave = spm.save();
                		getGame().getPhase().deployMeeple(pos, loc, SmallFollower.class);
                		phaseLoop();
                		currRank = rank();
                		PositionRanking meepleRanking = new PositionRanking(currRank, pos, rot);
                		meepleRanking.setAction(new MeepleAction(SmallFollower.class, pos, locs));
                		meepleRanking.setActionLocation(loc);
                		meepleRanking.setActionPosition(pos);
                		rankedPlacements.add(meepleRanking);
                		spm.restore(beforeMeepleSave);
                	}
                }
                spm.restore(sp);
                //TODO farin: fix hopefulGatePlacement
                //now rank meeple placements - must restore because rank change game
                //getGame().getPhase().placeTile(rot, pos);
                //hopefulGatePlacements.clear();
                //spm.restore(sp);
                //TODO farin: add best placements for MAGIC GATE
                //getGame().getPhase().enter();
            }
        }
        restoreGame();
        logger.info("Selected move is: {}", rankedPlacements.first());
        return rankedPlacements;
    }
    
    private TreeSet<PositionRanking> getPossibleMoves()
    {
    	Map<Position, Set<Rotation>> placements = getGame().getBoard().getAvailablePlacements();
    	return rankMoves(placements);
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
    	
    	for(PositionRanking ranking : getPossibleMoves())
    	{
    		doMove(ranking);
    		double value = -star25(alpha, beta, depth-1);
    		undoMove();
    		if(value >= beta) return value;
    		if(value >= alpha) alpha = value;
    		score = Math.max(score, value);
    	}
    	return score;
    }
    
    private double nProbe(double alpha, double beta, int depth, int probingFactor)
    {
    	int i = 0;
    	for(PositionRanking move : getPossibleMoves())
    	{    		
    			if(i++ >= probingFactor) break;
    			doMove(move);
    			double value = -star25(-beta, -alpha, depth - 1);
    			undoMove();
    			if(value >= beta) return beta;
    			if(value > alpha) alpha = value;
    	}
    	
    	return alpha;
    }
    
    private double star25(double alpha, double beta, int depth) {
    	swapCurrentPlayer();
    	if(packSize < 1)
    	{
    		swapCurrentPlayer();
    		return rankLeaf();
    	}
    	if(depth == 0)
    	{
    		swapCurrentPlayer();
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
    		int probingFactor = 2;//TODO: jtwigg: choose the probing factor more intelligently?
    		value = nProbe(ax, bx, depth, probingFactor);
    		replaceTile(tileId);
    		
    		cur_w += value;
    		if(value >= cur_beta)
    		{
    			swapCurrentPlayer();
    			return beta;
    		}
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
    		replaceTile(tileId);
    		if (value >=cur_beta)
    		{
    			swapCurrentPlayer();
    			return beta;
    		}
    		if (value <= cur_alpha)
    		{
    			swapCurrentPlayer();
    			return alpha;
    		}
    		cur_x += probability * value;
    	}
    	
    	swapCurrentPlayer();
    	return cur_x;

    }
}
