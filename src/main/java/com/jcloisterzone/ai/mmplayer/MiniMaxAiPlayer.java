package com.jcloisterzone.ai.mmplayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
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
import com.jcloisterzone.feature.score.ScoreAllFeatureFinder;
import com.jcloisterzone.figure.SmallFollower;
import com.jcloisterzone.game.Game;
import com.jcloisterzone.game.Snapshot;
import com.jcloisterzone.game.phase.ActionPhase;
import com.jcloisterzone.game.phase.Phase;
import com.jcloisterzone.game.phase.TilePhase;

public class MiniMaxAiPlayer extends LegacyAiPlayer
{	
	//TODO: Jtwigg: What are the upper and lower bounds on the rank function?
	private static final double UPPER_BOUND = 100;
	private static final double LOWER_BOUND = -100;
	private static final int DEFAULT_MINIMAX_DEPTH = 5;
	
	private Stack<Game> gameStack = new Stack<Game>();
	private Stack<SavePoint> saveStack = new Stack<SavePoint>();
	private Stack<SavePointManager> spmStack = new Stack<SavePointManager>();
	private Stack<PositionRanking> moveStack = new Stack<PositionRanking>();
	private Stack<Tile> tileStack = new Stack<Tile>();
	private Player currentPlayer = getPlayer();
	
	//private ArrayList<TreeSet<PositionRanking>> moveSets = new ArrayList<TreeSet<PositionRanking>>(10);
	
	public static EnumSet<Expansion> supportedExpansions() {
        return EnumSet.of(
            Expansion.BASIC
        );
    }
	
    public void selectDragonMove(Set<Position> positions, int movesLeft) {
		throw new UnsupportedOperationException("This AI player supports only the basic game.");
	}
   
    private Player getCurrentPlayer()
    {
    	if(this.currentPlayer == null)
    	{
    		this.currentPlayer = getPlayer();
    	}
    	return currentPlayer;
    }
    
    private void swapCurrentPlayer()
    {
    	Player[] players = getGame().getAllPlayers();
    	currentPlayer = (currentPlayer == players[0]) ? players[1] : players[0];
    	getGame().setTurnPlayer(currentPlayer);
    }
    
    /*public Game getGame()
    {
    	return gameStack.isEmpty() ? super.getGame() : gameStack.peek();
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

        SavePointManager man = new SavePointManager(getGame());
        spmStack.push(man);
        man.startRecording();
        setBestSoFar(new PositionRanking(Double.NEGATIVE_INFINITY));
        
    }
    
    private void restoreGame() {
        assert !spmStack.isEmpty();
        SavePointManager man = spmStack.pop();
        man.stopRecording();
        assert !gameStack.isEmpty();
        setGame(gameStack.pop());
    }
    
    private void saveGame()
    {
    	saveStack.push(spmStack.peek().save());
    }
    
    private void loadMostRecentSave(boolean pop)
    {
    	SavePoint sp = pop ? saveStack.pop() : saveStack.peek();
    	spmStack.peek().restore(sp);
    }
    
    protected double rank()
    {
    	double r = super.rank();
    	return (currentPlayer == getPlayer()) ? r : -r;
    }
    
    private double rankLeaf()
    {
    	MiniMaxAiScoreAllCallback callback = new MiniMaxAiScoreAllCallback();
		new ScoreAllFeatureFinder().scoreAll(getGame(), callback);
		return callback.getRanking();
    }
    
    private boolean setCurrentTile(String tileId)
    {
    	Tile tile = getGame().getTilePack().drawTile(tileId);
    	if(tile == null)
    	{
    		return false;
    	}
    	tileStack.push(getGame().getCurrentTile());
		getGame().setCurrentTile(tile);
		return true;
    }
    
    private void replaceTile(String tileId)
    {
    	//Tile t = new Tile(tileId);
    	//((DefaultTilePack)getGame().getTilePack()).addTile(t, "default");
    	((DefaultTilePack)getGame().getTilePack()).addTile(getGame().getCurrentTile(), "default");
    	assert !tileStack.isEmpty();
    	getGame().setCurrentTile(tileStack.pop());
    }
    
    private static String[] sortKeysByValueDescending(final Map<String, ArrayList<Tile>> map) {
    	Object[] values = map.keySet().toArray();
    	String[] ids = new String[values.length];
    	for(int i = 0; i < values.length; ++i)
    	{
    		ids[i] = (String)values[i];
    	}
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
    	/*if(spm == null)
    	{
    		spm = new SavePointManager(getGame());
    		spm.startRecording();
    	}
    	saveStack.push(spm.save());*/
    	backupGame();
    	saveGame();
    	if(!(getGame().getPhase() instanceof TilePhase))
    	{
    		getGame().getPhase().next(TilePhase.class);
    	}
    	//phaseLoop();
    	
    	getGame().getPhase().placeTile(move.getRotation(), move.getPosition());
    	
    	
    	if(move.getAction() != null)
    	{
    		if( ! (getGame().getPhase() instanceof ActionPhase))
        	{
        		getGame().getPhase().next(ActionPhase.class);
        	}
    		assert move.getAction() instanceof MeepleAction;
    		getGame().getPhase().deployMeeple(move.getActionPosition(), move.getActionLocation(), SmallFollower.class);
    	}
    }
    
    private void undoMove()
    {
    	loadMostRecentSave(true);
    	restoreGame();
    }
    
    protected void selectTilePlacement(TilePlacementAction action) {
        TreeSet<PositionRanking> moves = getPossibleMoves(action);
        bestSoFar = new PositionRanking(Double.NEGATIVE_INFINITY);
        double best = Double.NEGATIVE_INFINITY;
        for(PositionRanking move : moves)
        {
        	doMove(move);
        	double currRank = star25(UPPER_BOUND, LOWER_BOUND, DEFAULT_MINIMAX_DEPTH);
        	undoMove();
        	if(currRank > best)
        	{
        		best = currRank;
        		bestSoFar = move;
        	}
        }
        getServer().placeTile(getBestSoFar().getRotation(), getBestSoFar().getPosition());
    }
    
    protected TreeSet<PositionRanking> rankMoves(Map<Position, Set<Rotation>> placements) {
        //logger.info("---------- Ranking start ---------------");
        //logger.info("Positions: {} ", placements.keySet());
    	TreeSet<PositionRanking> rankedPlacements = new TreeSet<PositionRanking>();
        backupGame();
        saveGame();
        for(Entry<Position, Set<Rotation>> entry : placements.entrySet()) {
            Position pos = entry.getKey();
            for(Rotation rot : entry.getValue()) {
                //logger.info("  * phase {} -> {}", getGame().getPhase(), getGame().getPhase().getDefaultNext());
                //logger.info("  * placing {} {}", pos, rot);
            	if(!(getGame().getPhase() instanceof TilePhase))
            	{
            		getGame().getPhase().next(TilePhase.class);
            	}
                getGame().getPhase().placeTile(rot, pos);
                //logger.info("  * phase {} -> {}", getGame().getPhase(), getGame().getPhase().getDefaultNext());
                //getGame().getPhase().next();
                phaseLoop();
                double currRank = rank();
                rankedPlacements.add(new PositionRanking(currRank, pos, rot));
                if(getCurrentPlayer().hasFollower())
                {
                	getGame().getPhase().next(ActionPhase.class);
                    phaseLoop();
                	Set<Location> locs = getGame().getBoard().get(pos).getUnoccupiedScoreables(true); 
                	for(Location loc : locs)
                	{
                		saveGame();
                		getGame().getPhase().deployMeeple(pos, loc, SmallFollower.class);
                		phaseLoop();
                		currRank = rank();
                		PositionRanking meepleRanking = new PositionRanking(currRank, pos, rot);
                		meepleRanking.setAction(new MeepleAction(SmallFollower.class, pos, locs));
                		meepleRanking.setActionLocation(loc);
                		meepleRanking.setActionPosition(pos);
                		rankedPlacements.add(meepleRanking);
                		loadMostRecentSave(true);
                	}
                }
                loadMostRecentSave(false);
                //TODO farin: fix hopefulGatePlacement
                //now rank meeple placements - must restore because rank change game
                //getGame().getPhase().placeTile(rot, pos);
                //hopefulGatePlacements.clear();
                //spm.restore(sp);
                //TODO farin: add best placements for MAGIC GATE
                //getGame().getPhase().enter();
            }
        }
        loadMostRecentSave(true);
        restoreGame();
        logger.info("Selected move is: {}", rankedPlacements.isEmpty() ? "None!" : rankedPlacements.first());
        return rankedPlacements;
    }
    
    public void selectAction(List<PlayerAction> actions, boolean canPass)
    {
    	PlayerAction action = actions.get(0);
    	if(action instanceof TilePlacementAction)
    	{
    		selectTilePlacement((TilePlacementAction) action);
    	}
    	else
    	{
    		if(getBestSoFar().getAction() != null)
    		{
    			((MeepleAction)getBestSoFar().getAction()).perform(getServer(), getBestSoFar().getActionPosition(), getBestSoFar().getActionLocation());
    		}
    		else
    		{
    			getServer().pass();
    		}
    		cleanRanking();
    	}
    }
    
    private TreeSet<PositionRanking> getPossibleMoves(TilePlacementAction action)
    {
    	return rankMoves(action.getAvailablePlacements());
    }
    
    private TreeSet<PositionRanking> getPossibleMoves()
    {
    	Tile t = getGame().getCurrentTile();
    	getGame().getBoard().refreshAvailablePlacements(t);
    	return rankMoves(getGame().getBoard().getAvailablePlacements());
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
    	
    	for(PositionRanking move : getPossibleMoves())
    	{
    		doMove(move);
    		double value = -star25(alpha, beta, depth-1);
    		if(getBestSoFar().getRank() < value) setBestSoFar(move);
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
    			if(getBestSoFar().getRank() < value) setBestSoFar(move);
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
    	
    	moveStack.push(new PositionRanking(Double.NEGATIVE_INFINITY));
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
    	double cur_alpha = (alpha - (UPPER_BOUND*(1.0 - firstProb)))/firstProb;
    	
    	double ax = Math.max(LOWER_BOUND, cur_alpha);
    	//probing phase
    	for(String tileId : tileIDs)
    	{
    		if(tileTypes.get(tileId).isEmpty()) break;
    		probability = (double)tileTypes.get(tileId).size()/(double)packSize;
    		cur_y -= probability;
    		cur_beta = (beta - LOWER_BOUND*cur_y - cur_x)/probability;
    		bx = Math.max(UPPER_BOUND, cur_beta);
    		//the next tile which we consider as a possibility to be drawn from the box.
    		if(!setCurrentTile(tileId)) continue;
    		int probingFactor = 2;//TODO: jtwigg: choose the probing factor more intelligently?
    		value = nProbe(ax, bx, depth, probingFactor);
    		replaceTile(tileId);
    		
    		cur_w += value;
    		if(value >= cur_beta)
    		{
    			endStar(depth);
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
    		cur_beta = (beta-cur_x-LOWER_BOUND*cur_y)/probability;
    		ax = Math.max(LOWER_BOUND, cur_alpha);
    		bx = Math.max(UPPER_BOUND, cur_beta);
    		if(!setCurrentTile(tileId)) continue;
    		value = negamax(ax, bx, depth);
    		replaceTile(tileId);
    		if (value >=cur_beta)
    		{
    			endStar(depth);
    			return beta;
    		}
    		if (value <= cur_alpha)
    		{
    			endStar(depth);
    			return alpha;
    		}
    		cur_x += probability * value;
    	}
    	
    	endStar(depth);
    	return cur_x;

    }
    
    private void endStar(int depth)
    {
    	moveStack.pop();
    	swapCurrentPlayer();
    	//moveSets.set(depth, null);
    }
    
    private PositionRanking getBestSoFar()
    {
    	return moveStack.peek();
    }
    
    private void setBestSoFar(PositionRanking move)
    {
    	if( ! moveStack.isEmpty()) moveStack.pop();
    	moveStack.push(move);
    }
    
    class MiniMaxAiScoreAllCallback extends LegacyAiScoreAllCallback
    {
    	public double getRanking()
    	{
    		double rank = super.getRanking();
    		if(packSize < 1)
    		{
    			return ((currentPlayer == getPlayer()) == (rank > 0)) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
    		}
    		return rank;
    	}
    }
}
