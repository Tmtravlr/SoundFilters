package com.tmtravlr.soundfilters.utils;

import net.minecraft.command.arguments.BlockStateInput;
import net.minecraft.state.IProperty;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds a BlockStateInput and a way to compare it
 * @since November 2019
 */
public class ComparableBlockStateInput implements Comparable<ComparableBlockStateInput> {

    private BlockStateInput input;

    public ComparableBlockStateInput(BlockStateInput input) {
        this.input = input;
    }

    @Override
    public int compareTo(ComparableBlockStateInput other) {
        if (this == other) {
            return 0;
        } else if (other == null) {
            return 1;
        } else if (this.input == other.input) {
            return 0;
        } else if (this.input.getState().getBlock().getRegistryName() != null && !this.input.getState().getBlock().getRegistryName().equals(other.input.getState().getBlock().getRegistryName())) {
            return this.input.getState().getBlock().getRegistryName().compareTo(other.input.getState().getBlock().getRegistryName());
        } else if (!(this.input.getState().getValues().isEmpty() && other.input.getState().getValues().isEmpty())) {
            Set<IProperty> otherProperties = new HashSet<>(other.input.getState().getValues().keySet());
            for (IProperty property : this.input.getState().getValues().keySet()) {
                if (!otherProperties.contains(property)) {
                    return 1;
                } else {
                    otherProperties.remove(property);
                }

                int comparison = this.input.getState().getValues().get(property).toString().compareTo(other.input.getState().getValues().get(property).toString());

                if (comparison != 0) {
                    return comparison;
                }
            }
        }

        return 0;
    }
}
