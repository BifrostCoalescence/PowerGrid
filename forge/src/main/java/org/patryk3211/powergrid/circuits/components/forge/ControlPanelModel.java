/*
 * Copyright 2025 patryk3211
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.patryk3211.powergrid.circuits.components.forge;

import com.mojang.math.Transformation;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.patryk3211.powergrid.PowerGrid;
import org.patryk3211.powergrid.circuits.circuitboard.ControlPanelBlock;
import org.patryk3211.powergrid.circuits.circuitboard.ControlPanelBlockEntity;
import org.patryk3211.powergrid.circuits.circuitboard.ControlPanelModelQuads;
import org.patryk3211.powergrid.circuits.components.ComponentModels;
import org.patryk3211.powergrid.circuits.components.IRenderedComponent;
import org.patryk3211.powergrid.circuits.components.properties.Orientation;
import org.patryk3211.powergrid.circuits.schematic.Area;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.circuits.schematic.Point;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

import static org.patryk3211.powergrid.circuits.schematic.CircuitLayer.GRID_TO_GRID_SCALE;

@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ControlPanelModel implements BakedModel {
    public static final ModelResourceLocation MODEL_ID = new ModelResourceLocation(PowerGrid.asResource("control_panel"), "");
    public static final ResourceLocation BASE_MODEL = PowerGrid.asResource("block/control_panel");

    public static final ModelProperty<ControlPanelBlockEntity> ENTITY = new ModelProperty<>();
    public static final ModelProperty<List<Area>> FRONT_LAYER = new ModelProperty<>();
    public static final ModelProperty<List<Point>> PADS = new ModelProperty<>();
    public static final ModelProperty<List<PlacedComponent>> COMPONENTS = new ModelProperty<>();

    private static final FaceBakery bakery = new FaceBakery();

    private final TextureAtlasSprite particleSprite;
    private final BakedModel baseModel;

    public ControlPanelModel(BakedModel plateModel) {
        this.baseModel = plateModel;
        this.particleSprite = baseModel.getParticleIcon();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, RandomSource random) {
        return baseModel.getQuads(state, face, random);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return false;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particleSprite;
    }

    @Override
    public ItemTransforms getTransforms() {
        return ItemTransforms.NO_TRANSFORMS;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        var be = level.getBlockEntity(pos);
        if(be instanceof ControlPanelBlockEntity circuit) {
            // Emit components
            var schematic = circuit.getSchematic();
            return ModelData.builder()
                    .with(COMPONENTS, List.copyOf(schematic.components()))
                    .with(ENTITY, circuit)
                    .build();
        }
        return modelData;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        var circuit = data.get(ENTITY);
        if(circuit != null) {
            if(circuit.quads != null) {
                var cached = circuit.quads.getQuads(state, side, renderType);
                if (cached != null)
                    return cached;
            } else {
                circuit.quads = new ControlPanelModelQuads();
            }
        }
        var quads = new ArrayList<>(baseModel.getQuads(state, side, rand, data, renderType));

        if(data.has(COMPONENTS)) {
            var components = data.get(COMPONENTS);
            for(var placed : components) {
                if(placed instanceof IRenderedComponent rendered && !rendered.emitBaked())
                    continue;
                var model = ComponentModels.getModel(placed);

                IQuadTransformer transformer;
                if(placed.has(Orientation.PROPERTY)) {
                    var orientation = placed.get(Orientation.PROPERTY);
                    var footprint = placed.component.footprint(placed);
                        transformer = QuadTransformers.applying(new Transformation(
                                new Vector3f(placed.x / 16f - 0.5f, 4 / 16f - 0.5f, placed.y / 16f - 0.5f),
                                new Quaternionf().rotationY(orientation.ordinal() * (float) Math.PI * 0.5f),
                                null, null
                        ));
                    if(orientation != Orientation.RIGHT) {
                        transformer = transformer.andThen(QuadTransformers.applying(new Transformation(
                                switch(orientation) {
                                    case DOWN -> new Vector3f(footprint.getOriginalHeight() / 16f, 0, 0);
                                    case LEFT -> new Vector3f(footprint.getOriginalWidth() / 16f, 0, footprint.getOriginalHeight() / 16f);
                                    case UP -> new Vector3f(0, 0, footprint.getOriginalWidth() / 16f);
                                    case RIGHT -> throw new IllegalStateException();
                                }, null, null, null
                        )));
                    }
                } else {
                    transformer = QuadTransformers.applying(
                            new Transformation(new Vector3f(placed.x / 16f - 0.5f, 4 / 16f - 0.5f, placed.y / 16f - 0.5f),
                                    null, null, null)
                    );
                }
                if(placed.destroyed) {
                    transformer = transformer.andThen(QuadTransformers.applyingColor(64, 64, 64));
                }
                var componentQuads = model.getQuads(state, side, rand, data, renderType);
                quads.addAll(transformer.process(componentQuads));
            }
        }

        if(state != null) {
            var transformer = QuadTransformers.applying(new Transformation(
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()
                            .rotateY((float) Math.PI * ControlPanelBlock.getAngleY(state) / 180f)
                            .rotateX((float) Math.PI * ControlPanelBlock.getAngleX(state) / 180f),
                    null, null
            ));
            var trQuads = transformer.process(quads);
            if(circuit != null && circuit.quads != null)
                circuit.quads.putQuads(state, side, renderType, trQuads);
            return trQuads;
        }
        if(circuit != null && circuit.quads != null)
            circuit.quads.putQuads(state, side, renderType, quads);
        return quads;
    }
}
