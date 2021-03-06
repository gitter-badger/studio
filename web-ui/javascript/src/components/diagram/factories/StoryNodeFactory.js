/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import * as SRD from 'storm-react-diagrams';

import StoryNodeModel from '../models/StoryNodeModel';
import StoryNodeWidget from '../widgets/StoryNodeWidget';


// Factory is in charge of creating a widget from a given model, and of initializing models
class StoryNodeFactory extends SRD.AbstractNodeFactory {

    constructor(updateCanvas) {
        super("story");
        this.updateCanvas = updateCanvas;
    }

    generateReactWidget(diagramEngine, node) {
        return <StoryNodeWidget diagramEngine={diagramEngine} node={node} updateCanvas={this.updateCanvas} />;
    }

    getNewInstance(initialConfig) {
        return new StoryNodeModel();
    }

}

export default StoryNodeFactory;
