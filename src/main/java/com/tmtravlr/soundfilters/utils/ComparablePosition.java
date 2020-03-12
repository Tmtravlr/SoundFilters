package com.tmtravlr.soundfilters.utils;

import net.minecraft.util.math.MathHelper;

/**
 * Represents a x, y, z position which has a comparator and a few methods
 * that make comparing it with another ComparablePositions very fast. Used
 * for fast-access sets/maps.
 *
 * @author Rebeca Rey (Tmtravlr)
 * @since 2014
 */
public class ComparablePosition implements Comparable<ComparablePosition> {
    private float x;
    private float y;
    private float z;

    public ComparablePosition(float xToSet, float yToSet, float zToSet) {
        this.x = xToSet;
        this.y = yToSet;
        this.z = zToSet;
    }

    // Return the int floor of x
    public int x() {
        return MathHelper.floor(x);
    }

    // Return the int floor of y
    public int y() {
        return MathHelper.floor(y);
    }

    // Return the int floor of z
    public int z() {
        return MathHelper.floor(z);
    }

    public boolean equals(Object object) {
        if (!(object instanceof ComparablePosition)) {
            return false;
        }
        ComparablePosition toCompare = (ComparablePosition) object;

        return toCompare.compareTo(this) == 0;
    }

    public int compareTo(ComparablePosition toCompare) {
        if (toCompare.x - this.x > 0.1) {
            return 1;
        } else if (toCompare.x - this.x < -0.1) {
            return -1;
        } else if (toCompare.y - this.y > 0.1) {
            return 1;
        } else if (toCompare.y - this.y < -0.1) {
            return -1;
        } else if (toCompare.z - this.z > 0.1) {
            return 1;
        } else if (toCompare.z - this.z < -0.1) {
            return -1;
        } else {
            return 0;
        }
    }

    @Override
    public String toString() {
        return "[" + x + ", " + y + ", " + z + "]";
    }
}
