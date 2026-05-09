<?php

namespace App\Notice\Request;

use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

/**
 * フェーズ19: お知らせ編集 FormRequest（規約 4-1 / config 駆動）。
 */
class UpdateNoticeRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function rules(): array
    {
        return [
            'subject'      => ['required', 'string', 'min:1', 'max:'.config('app.notice.subject_max_length')],
            'categoryId'   => ['required', 'integer', Rule::in([
                config('app.notice.categories.important_id'),
                config('app.notice.categories.normal_id'),
            ])],
            'body'         => ['required', 'string', 'min:1', 'max:'.config('app.notice.body_max_length')],
            'publishStart' => ['required', 'date'],
            'publishEnd'   => ['required', 'date', 'after_or_equal:publishStart'],
        ];
    }
}
