<?php

namespace App\Workflow\Request;

use Illuminate\Foundation\Http\FormRequest;

class CreateWorkflowFormRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function rules(): array
    {
        return config('app.workflow.create_validation');
    }
}
