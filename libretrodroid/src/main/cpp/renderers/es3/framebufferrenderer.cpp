/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "framebufferrenderer.h"
#include "es3utils.h"
#include "../../log.h"

namespace libretrodroid {

FramebufferRenderer::FramebufferRenderer(
    unsigned width,
    unsigned height,
    bool depth,
    bool stencil,
    ShaderManager::Chain shaders
) {
    this->depth = depth;
    this->stencil = stencil;
    this->width = width;
    this->height = height;
    this->shaders = std::move(shaders);

    initializeBuffers();
}

void FramebufferRenderer::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    Renderer::onNewFrame(data, width, height, pitch);

    if (isDirty) {
        initializeBuffers();
        isDirty = false;
    }

    GLboolean scissorWasEnabled = glIsEnabled(GL_SCISSOR_TEST);
    if (scissorWasEnabled) glDisable(GL_SCISSOR_TEST);
    glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer->framebuffer);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, presentBuffer->framebuffer);
    glBlitFramebuffer(
        0,
        0,
        this->width,
        this->height,
        0,
        0,
        this->width,
        this->height,
        GL_COLOR_BUFFER_BIT,
        GL_NEAREST
    );
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (scissorWasEnabled) glEnable(GL_SCISSOR_TEST);
}

void FramebufferRenderer::initializeBuffers() {
    framebuffers = ES3Utils::buildShaderPasses(width, height, shaders);

    bool needsMainFramebuffer =
        framebuffer->framebuffer == 0 ||
        framebuffer->width != width ||
        framebuffer->height != height;

    if (needsMainFramebuffer) {
        ES3Utils::deleteFramebuffer(std::move(framebuffer));
        framebuffer = ES3Utils::createFramebuffer(
            width,
            height,
            shaders.linearTexture,
            false,
            depth,
            stencil
        );
    } else {
        GLint filter = shaders.linearTexture ? GL_LINEAR : GL_NEAREST;
        glBindTexture(GL_TEXTURE_2D, framebuffer->texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    bool needsPresentBuffer =
        presentBuffer->framebuffer == 0 ||
        presentBuffer->width != width ||
        presentBuffer->height != height;

    if (needsPresentBuffer) {
        ES3Utils::deleteFramebuffer(std::move(presentBuffer));
        presentBuffer = ES3Utils::createFramebuffer(
            width,
            height,
            shaders.linearTexture,
            false,
            false,
            false
        );
    } else {
        GLint filter = shaders.linearTexture ? GL_LINEAR : GL_NEAREST;
        glBindTexture(GL_TEXTURE_2D, presentBuffer->texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glBindTexture(GL_TEXTURE_2D, 0);
    }
}

uintptr_t FramebufferRenderer::getTexture() {
    return presentBuffer->texture;
}

uintptr_t FramebufferRenderer::getFramebuffer() {
    if (isDirty) {
        initializeBuffers();
        isDirty = false;
    }
    if (!framebuffer || framebuffer->framebuffer == 0) {
        return 0;
    }
    return framebuffer->framebuffer;
}

void FramebufferRenderer::setPixelFormat(int pixelFormat) {
    // TODO... Here we should handle 32bit framebuffers.
}

void FramebufferRenderer::updateRenderedResolution(unsigned int width, unsigned int height) {
    if (width == 0 || height == 0) {
        LOGI("FramebufferRenderer ignoring 0x0 resolution (keeping %dx%d)", this->width, this->height);
        return;
    }
    if (width > 4096) width = 4096;
    if (height > 4096) height = 4096;
    if (this->width != width || this->height != height) {
        LOGI("FramebufferRenderer resolution change: %dx%d -> %dx%d", this->width, this->height, width, height);
        this->width = width;
        this->height = height;
        initializeBuffers();
        isDirty = false;
    }
}

bool FramebufferRenderer::rendersInVideoCallback() {
    return true;
}

void FramebufferRenderer::setShaders(ShaderManager::Chain shaders) {
    if (shaders != this->shaders) {
        this->shaders = shaders;
        isDirty = true;
    }
}

Renderer::PassData FramebufferRenderer::getPassData(unsigned int layer) {
    PassData result;

    if (layer >= 0 && layer < framebuffers->size()) {
        result.framebuffer = framebuffers->at(layer)->framebuffer;
        result.width = framebuffers->at(layer)->width;
        result.height = framebuffers->at(layer)->height;
    }

    if (layer > 0 && layer < framebuffers->size() + 1) {
        result.texture = framebuffers->at(layer - 1)->texture;
    }

    return result;
}

} //namespace libretrodroid
