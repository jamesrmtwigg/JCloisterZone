package com.jcloisterzone.action;

import com.jcloisterzone.board.Location;
import com.jcloisterzone.board.Position;
import com.jcloisterzone.rmi.Client2ClientIF;

public class UndeployAction extends SelectFollowerAction {

    public UndeployAction(String name) {
        super(name);
    }

    @Override
    public void perform(Client2ClientIF server, Position pos, Location loc) {
        server.undeployMeeple(pos, loc, getMeepleType(pos, loc));
    }

}
