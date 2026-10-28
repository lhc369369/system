#!/bin/bash
if ! IFS= read -r -p "请输入一个整数：" number; then
    printf '错误：未读取到输入。\n' >&2; exit 1
fi
if [[ ! "$number" =~ ^[+-]?[0-9]+$ ]]; then
    printf '错误：请输入有效整数。\n' >&2; exit 1
fi
last_digit=${number: -1}
if (( last_digit % 2 == 0 )); then printf '%s 是偶数。\n' "$number"; else printf '%s 是奇数。\n' "$number"; fi
