import React from "react";
import styled from "styled-components";
import {
  Tag,
  Intent as BlueprintIntent,
  AnchorButton,
  Button,
  Classes,
} from "@blueprintjs/core";
import { Intent, BlueprintButtonIntentsCSS } from "constants/DefaultTheme";

export type MessageAction = {
  url?: string;
  onClick?: (e: React.SyntheticEvent) => void;
  text: string;
  intent: Intent;
};

const StyledTag = styled(Tag)`
  &&& {
    padding: ${props => props.theme.spaces[8]}px;
    font-size: ${props => props.theme.fontSizes[4]}px;
    &.${Classes.TAG} {
      background: none;
    }
    text-align: center;
    margin-bottom: ${props => props.theme.spaces[4]}px;
    p {
      white-space: normal;
      margin: 0;
    }
  }
`;

const ActionsContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  & .appsmith-message-action-button {
    border: none;
    ${BlueprintButtonIntentsCSS}
  }
`;

const ActionButton = (props: MessageAction) => {
  if (props.url) {
    return (
      <AnchorButton
        className="appsmith-message-action-button"
        href={props.url}
        text={props.text}
        intent={props.intent as BlueprintIntent}
      />
    );
  } else if (props.onClick) {
    return (
      <Button
        className="appsmith-message-action-button"
        onClick={props.onClick}
        text={props.text}
        intent={props.intent as BlueprintIntent}
      />
    );
  }
  return null;
};

export type FormMessageProps = {
  intent: Intent;
  message: string;
  title?: string;
  actions?: MessageAction[];
};

export const FormMessage = (props: FormMessageProps) => {
  const actions =
    props.actions &&
    props.actions.map(action => <ActionButton key={action.text} {...action} />);
  return (
    <StyledTag fill large minimal intent={props.intent as BlueprintIntent}>
      {props.title && <h4>{props.title}</h4>}
      <p>{props.message}</p>
      {actions && <ActionsContainer>{actions}</ActionsContainer>}
    </StyledTag>
  );
};

export default FormMessage;
