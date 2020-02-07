import React from "react";
import { reduxForm, InjectedFormProps, FormSubmitHandler } from "redux-form";
import { HTTP_METHOD_OPTIONS } from "constants/ApiEditorConstants";
import styled from "styled-components";
import FormLabel from "components/editorComponents/FormLabel";
import FormRow from "components/editorComponents/FormRow";
import { BaseButton } from "components/designSystems/blueprint/ButtonComponent";
import { RestAction, PaginationField } from "api/ActionAPI";
import TextField from "components/editorComponents/form/fields/TextField";
import DynamicTextField from "components/editorComponents/form/fields/DynamicTextField";
import DropdownField from "components/editorComponents/form/fields/DropdownField";
import DatasourcesField from "components/editorComponents/form/fields/DatasourcesField";
import KeyValueFieldArray from "components/editorComponents/form/fields/KeyValueFieldArray";
import ApiResponseView from "components/editorComponents/ApiResponseView";
import { API_EDITOR_FORM_NAME } from "constants/forms";
import LoadingOverlayScreen from "components/editorComponents/LoadingOverlayScreen";
import { FormIcons } from "icons/FormIcons";
import { BaseTabbedView } from "components/designSystems/appsmith/TabbedView";
import Pagination, { PaginationType } from "./Pagination";

const Form = styled.form`
  display: flex;
  flex-direction: column;
  height: calc(100vh - ${props => props.theme.headerHeight});
  width: 100%;
  ${FormLabel} {
    padding: ${props => props.theme.spaces[3]}px;
  }
  ${FormRow} {
    padding: ${props => props.theme.spaces[2]}px;
    & > * {
      margin-right: 5px;
    }
    ${FormLabel} {
      padding: 0;
      width: 100%;
    }
  }
`;

const MainConfiguration = styled.div`
  padding-top: 10px;
`;

const SecondaryWrapper = styled.div`
  display: flex;
  height: 100%;
  border-top: 1px solid #d0d7dd;
`;

const RequestParamsWrapper = styled.div`
  flex: 4;
  border-right: 1px solid #d0d7dd;
  height: 100%;
  overflow-y: scroll;
  padding-top: 6px;
`;

const ActionButtons = styled.div`
  flex: 1;
`;

const ActionButton = styled(BaseButton)`
  &&& {
    max-width: 72px;
    margin: 0 5px;
    min-height: 30px;
  }
`;

const JSONEditorFieldWrapper = styled.div`
  margin: 5px;
`;

const DatasourceWrapper = styled.div`
  width: 100%;
  max-width: 320px;
`;

const TabbedViewContainer = styled.div`
  flex: 1;
  padding-top: 12px;
`;

interface APIFormProps {
  pluginId: string;
  allowSave: boolean;
  allowPostBody: boolean;
  onSubmit: FormSubmitHandler<RestAction>;
  onSaveClick: () => void;
  onRunClick: (paginationField?: PaginationField) => void;
  onDeleteClick: () => void;
  isSaving: boolean;
  isRunning: boolean;
  isDeleting: boolean;
  paginationType: PaginationType;
}

type Props = APIFormProps & InjectedFormProps<RestAction, APIFormProps>;

const ApiEditorForm: React.FC<Props> = (props: Props) => {
  const {
    pluginId,
    allowSave,
    allowPostBody,
    onSaveClick,
    onDeleteClick,
    onRunClick,
    handleSubmit,
    isDeleting,
    isRunning,
    isSaving,
  } = props;
  return (
    <Form onSubmit={handleSubmit}>
      {isSaving && <LoadingOverlayScreen>Saving...</LoadingOverlayScreen>}
      <MainConfiguration>
        <FormRow>
          <TextField
            name="name"
            placeholder="nameOfApi (camel case)"
            showError
          />
          <ActionButtons>
            <ActionButton
              text="Delete"
              accent="error"
              onClick={onDeleteClick}
              loading={isDeleting}
            />
            <ActionButton
              text="Run"
              accent="secondary"
              onClick={() => {
                onRunClick();
              }}
              loading={isRunning}
            />
            <ActionButton
              text="Save"
              accent="primary"
              filled
              onClick={onSaveClick}
              loading={isSaving}
              disabled={!allowSave}
            />
          </ActionButtons>
        </FormRow>
        <FormRow>
          <DropdownField
            placeholder="Method"
            name="actionConfiguration.httpMethod"
            options={HTTP_METHOD_OPTIONS}
          />
          <DatasourceWrapper>
            <DatasourcesField name="datasource.id" pluginId={pluginId} />
          </DatasourceWrapper>
          <DynamicTextField
            placeholder="v1/method"
            name="actionConfiguration.path"
            leftIcon={FormIcons.SLASH_ICON}
          />
        </FormRow>
      </MainConfiguration>
      <SecondaryWrapper>
        <TabbedViewContainer>
          <BaseTabbedView
            tabs={[
              {
                key: "apiInput",
                title: "API Input",
                panelComponent: (
                  <RequestParamsWrapper>
                    <KeyValueFieldArray
                      name="actionConfiguration.headers"
                      label="Headers"
                    />
                    <KeyValueFieldArray
                      name="actionConfiguration.queryParameters"
                      label="Params"
                    />
                    {allowPostBody && (
                      <React.Fragment>
                        <FormLabel>{"Post Body"}</FormLabel>
                        <JSONEditorFieldWrapper>
                          <DynamicTextField
                            name="actionConfiguration.body"
                            height={300}
                            showLineNumbers
                            allowTabIndent
                          />
                        </JSONEditorFieldWrapper>
                      </React.Fragment>
                    )}
                  </RequestParamsWrapper>
                ),
              },
              {
                key: "pagination",
                title: "Pagination",
                panelComponent: (
                  <Pagination
                    onTestClick={props.onRunClick}
                    paginationType={props.paginationType}
                  ></Pagination>
                ),
              },
            ]}
          ></BaseTabbedView>
        </TabbedViewContainer>

        <ApiResponseView />
      </SecondaryWrapper>
    </Form>
  );
};

export default reduxForm<RestAction, APIFormProps>({
  form: API_EDITOR_FORM_NAME,
  enableReinitialize: true,
})(ApiEditorForm);
