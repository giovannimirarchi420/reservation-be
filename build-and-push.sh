#!/bin/bash

# Configuration
IMAGE_NAME="resource-management"
VERSION="1.0.0"
REGISTRY=docker.io/g420

# Full image name
FULL_IMAGE_NAME="$REGISTRY/$IMAGE_NAME:$VERSION"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}Building Docker image: $FULL_IMAGE_NAME${NC}"
docker build -t $FULL_IMAGE_NAME .

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Successfully built image: $FULL_IMAGE_NAME${NC}"
    
    echo -e "${GREEN}Pushing image to registry: $REGISTRY${NC}"
    docker push $FULL_IMAGE_NAME
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Successfully pushed image: $FULL_IMAGE_NAME${NC}"
    else
        echo -e "${RED}Failed to push image${NC}"
        exit 1
    fi
else
    echo -e "${RED}Failed to build image${NC}"
    exit 1
fi

echo -e "${GREEN}Image is now available at: $FULL_IMAGE_NAME${NC}"