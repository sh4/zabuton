#pragma once

#include <utility>

#define ZABUTON_DETAIL_CONCAT_EXPAND(a, b) a##b
#define ZABUTON_DETAIL_CONCAT(a, b) ZABUTON_DETAIL_CONCAT_EXPAND(a, b)
#define ZABUTON_MAKE_SCOPE(lambda) auto ZABUTON_DETAIL_CONCAT(zabutonScopeGuard_, __COUNTER__) = ::zabuton::util::MakeScopeGuard(lambda);

namespace zabuton { namespace util {

template <typename T>
class ScopeGuard
{
    T&& lambda_;
public:
    explicit ScopeGuard(T&& lambda) : lambda_(std::forward<T>(lambda)) {
    }
    ScopeGuard(ScopeGuard&& that) : lambda_(std::move(that.lambda_)) {
    }
    ~ScopeGuard() {
        lambda_();
    }
};

template <typename T>
ScopeGuard<T> MakeScopeGuard(T&& lambda) {
    return ScopeGuard<T>(std::forward<T>(lambda));
}

}}