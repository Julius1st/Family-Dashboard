FROM mcr.microsoft.com/devcontainers/base:trixie

ARG LOCALE=C.UTF-8
ARG TIME_ZONE=Europe/Berlin
ARG USERNAME=devuser
ARG NODE_VERSION=24

ENV LANGUAGE=${LOCALE}
ENV LC_ALL=${LOCALE}
ENV TZ=${TIME_ZONE}

# Install development tools
RUN apt-get update && apt-get upgrade -y && apt-get install -y --no-install-recommends \
    fd-find \
    locales \
    sqlite3 \
    tmux \
    ripgrep

# Set locale
RUN sed -i -E "s/# (${LOCALE})/\1/" /etc/locale.gen \
    && locale-gen ${LOCALE}

# Rename existing vscode user
RUN if [ "${USERNAME}" != "vscode" ]; then \
        groupmod -n ${USERNAME} vscode && \
        usermod -l ${USERNAME} -d /home/${USERNAME} -m vscode && \
        sed -i "s/vscode/${USERNAME}/g" /etc/sudoers.d/vscode && \
        mv /etc/sudoers.d/vscode /etc/sudoers.d/${USERNAME}; \
    fi

# Install Node.js
RUN curl -fsSL https://deb.nodesource.com/setup_${NODE_VERSION}.x | bash - \
    && apt-get install -y nodejs

# Node.js settings
ENV NPM_CONFIG_PREFIX=/home/${USERNAME}/.npm-global
ENV PATH=/home/${USERNAME}/.npm-global/bin:$PATH
RUN mkdir -p /home/${USERNAME}/.npm-global \
    && chown -R ${USERNAME}:${USERNAME} /home/${USERNAME}/.npm-global