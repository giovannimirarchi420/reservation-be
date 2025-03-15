#!/bin/bash

# Configuration
IMAGE_NAME="resource-management-backend"
REGISTRY="docker.io/g420"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

# Extract version from pom.xml
echo -e "${YELLOW}Extracting version from pom.xml...${NC}"
if [ ! -f pom.xml ]; then
    echo -e "${RED}Error: pom.xml not found${NC}"
    exit 1
fi

# Utilizzo di sed che è più portabile tra sistemi Unix/Linux/macOS
VERSION=$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -1 | sed 's/-SNAPSHOT//')
if [ -z "$VERSION" ]; then
    echo -e "${RED}Error: Could not extract version from pom.xml${NC}"
    echo -e "${YELLOW}Using default version: 1.0.0${NC}"
    VERSION="1.0.0"
else
    echo -e "${GREEN}Found version: $VERSION${NC}"
fi

# Full image names with different tags
VERSION_TAG="$REGISTRY/$IMAGE_NAME:$VERSION"
LATEST_TAG="$REGISTRY/$IMAGE_NAME:latest"

echo -e "${GREEN}Building Docker image...${NC}"
docker build -t $VERSION_TAG -t $LATEST_TAG .

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Successfully built images:${NC}"
    echo -e "  - $VERSION_TAG"
    echo -e "  - $LATEST_TAG"
    
    echo -e "${GREEN}Pushing images to registry: $REGISTRY${NC}"
    
    # Push version tag
    echo -e "${YELLOW}Pushing $VERSION_TAG...${NC}"
    docker push $VERSION_TAG
    VERSION_PUSH_STATUS=$?
    
    # Push latest tag
    echo -e "${YELLOW}Pushing $LATEST_TAG...${NC}"
    docker push $LATEST_TAG
    LATEST_PUSH_STATUS=$?
    
    if [ $VERSION_PUSH_STATUS -eq 0 ] && [ $LATEST_PUSH_STATUS -eq 0 ]; then
        echo -e "${GREEN}Successfully pushed all images${NC}"
    else
        echo -e "${RED}Failed to push one or more images${NC}"
        exit 1
    fi
else
    echo -e "${RED}Failed to build images${NC}"
    exit 1
fi

echo -e "${GREEN}Images are now available at:${NC}"
echo -e "  - $VERSION_TAG"
echo -e "  - $LATEST_TAG"