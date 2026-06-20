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
package org.patryk3211.powergrid.circuits.components.fabric;

import com.mojang.math.Transformation;
import io.github.fabricators_of_create.porting_lib.models.QuadTransformers;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.patryk3211.powergrid.PowerGrid;
import org.patryk3211.powergrid.circuits.circuitboard.ControlPanelBlock;
import org.patryk3211.powergrid.circuits.circuitboard.ControlPanelBlockEntity;
import org.patryk3211.powergrid.circuits.components.ComponentModels;
import org.patryk3211.powergrid.circuits.components.IRenderedComponent;
import org.patryk3211.powergrid.circuits.components.properties.Orientation;
import org.patryk3211.powergrid.circuits.schematic.Area;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;
import org.patryk3211.powergrid.circuits.schematic.Point;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.patryk3211.powergrid.circuits.schematic.CircuitLayer.GRID_SIZE;

@Environment(EnvType.CLIENT)
public class ControlPanelModel implements UnbakedModel, BakedModel {
    public static final ModelResourceLocation MODEL_ID = new ModelResourceLocation(new ResourceLocation(PowerGrid.MOD_ID, "circuit_board"), "");
    public static final ResourceLocation BASE_MODEL = PowerGrid.asResource("block/circuit_board");


    private TextureAtlasSprite particleSprite;
    private BakedModel baseModel;

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
    public Collection<ResourceLocation> getDependencies() {
        return List.of(BASE_MODEL);
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelLoader) {

    }

    @Override
    public @Nullable BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> textureGetter, ModelState rotationContainer, ResourceLocation modelId) {
        baseModel = baker.bake(BASE_MODEL, rotationContainer);
        particleSprite = baseModel.getParticleIcon();
        return this;
    }

    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
        context.pushTransform(QuadTransformers.applying(new Transformation(
                new Vector3f(0.5f, 0.5f, 0.5f),
                new Quaternionf()
                        .rotateY((float) Math.PI * ControlPanelBlock.getAngleY(state) / 180f)
                        .rotateX((float) Math.PI * ControlPanelBlock.getAngleX(state) / 180f),
                null, null
        )));
        baseModel.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        var be = blockView.getBlockEntity(pos);

        if(be instanceof ControlPanelBlockEntity circuit) {
            // Emit components
            var schematic = circuit.getSchematic();
            for(var placed : schematic.components()) {
                if(placed instanceof IRenderedComponent rendered && !rendered.emitBaked())
                    continue;
                var model = ComponentModels.getModel(placed);
                int color = placed.destroyed ? 0xFF404040 : -1;
                if(placed.has(Orientation.PROPERTY)) {
                    var orientation = placed.get(Orientation.PROPERTY);
                    var footprint = placed.component.footprint(placed);
                    context.pushTransform(new RotateOffsetTransform(placed.x, 4, placed.y, orientation, footprint.getOriginalWidth(), footprint.getOriginalHeight(), color));
                } else {
                    context.pushTransform(new OffsetTransform(placed.x, 4, placed.y, color));
                }
                model.emitBlockQuads(blockView, state, pos, randomSupplier, context);
                context.popTransform();
            }
        }

        context.popTransform();
    }

    private static class OffsetTransform implements RenderContext.QuadTransform {
        protected final float x, y, z;
        protected final int color;

        public OffsetTransform(int x, int y, int z, int color) {
            this.x = x / 16f - 0.5f;
            this.y = y / 16f - 0.5f;
            this.z = z / 16f - 0.5f;
            this.color = color;
        }

        @Override
        public boolean transform(MutableQuadView view) {
            for(int i = 0; i < 4; ++i) {
                var x = view.x(i);
                var y = view.y(i);
                var z = view.z(i);
                view.pos(i, x + this.x, y + this.y, z + this.z);
                view.color(i, color);
            }
            return true;
        }
    }

    private static class RotateOffsetTransform extends OffsetTransform {
        protected final Orientation orientation;
        protected final float width, height;

        public RotateOffsetTransform(int x, int y, int z, Orientation orientation, int width, int height, int color) {
            super(x, y, z, color);
            this.orientation = orientation;
            this.width = width / 16f;
            this.height = height / 16f;
        }

        @Override
        public boolean transform(MutableQuadView view) {
            for(int i = 0; i < 4; ++i) {
                var x = view.x(i);
                var y = view.y(i);
                var z = view.z(i);
                switch(orientation) {
                    case DOWN -> {
                        // 90 degrees
                        var buf = x;
                        x = this.height - z;
                        z = buf;
                    }
                    case LEFT -> {
                        // 180 degrees
                        x = this.width - x;
                        z = this.height - z;
                    }
                    case UP -> {
                        // 270 degrees
                        var buf = z;
                        z = this.width - x;
                        x = buf;
                    }
                }
                view.pos(i, x + this.x, y + this.y, z + this.z);
            }
            return true;
        }
    }
}
