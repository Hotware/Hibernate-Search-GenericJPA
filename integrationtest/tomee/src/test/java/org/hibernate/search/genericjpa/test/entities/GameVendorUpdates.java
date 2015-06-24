/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

import org.hibernate.search.genericjpa.annotations.Event;
import org.hibernate.search.genericjpa.annotations.IdFor;
import org.hibernate.search.genericjpa.annotations.Updates;

@Entity
@Table(name = "Game_VendorUpdates")
@Updates(tableName = "Game_VendorUpdates", originalTableName = "Game_Vendor")
public class GameVendorUpdates implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @IdFor(entityClass = Game.class, columns = "gameId", columnsInOriginal = "game_ID")
    @Column(name = "gameId")
    private Long gameId;

    @IdFor(entityClass = Vendor.class, columns = "vendorId", columnsInOriginal = "vendors_ID")
    @Column(name = "vendorId")
    private Long vendorId;

    @Event(column = "eventType")
    @Column(name = "eventType")
    private Integer eventType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public Integer getEventType() {
        return eventType;
    }

    public void setEventType(Integer eventType) {
        this.eventType = eventType;
    }

}
