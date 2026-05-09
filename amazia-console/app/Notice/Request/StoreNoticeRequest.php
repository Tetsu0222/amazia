<?php

namespace App\Notice\Request;

use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

/**
 * フェーズ19: お知らせ新規作成 FormRequest（規約 4-1 / config 駆動）。
 *
 * publish_start <= publish_end の検証は Laravel の after_or_equal で実施。
 * 時分秒の補完は CreateNoticeService 側で行うため、ここでは 'YYYY-MM-DD' / フル ISO のいずれも受け付ける。
 */
class StoreNoticeRequest extends FormRequest
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
