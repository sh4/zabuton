#include <string>
#include <jni.h>
#include <git2.h>
#include <memory>
#include <cstdint>
#include <cerrno>
#include <string_view>
#include <vector>
#include "util.h"

#define ZABUTON_ENSURE_LIBGIT2_NOERROR(env, op) if (ensureNoErrorLibGit2(env, (op)) < 0) { return; }
#define ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(env, op, ret) if (ensureNoErrorLibGit2(env, (op)) < 0) { return (ret); }

namespace
{

int ensureNoErrorLibGit2(JNIEnv *env, int returnCode)
{
    if (returnCode >= 0) {
        return returnCode;
    }
    const char *exceptionClassName = "io/github/sh4/zabuton/git/LibGit2Exception";
    jclass exceptionClass = env->FindClass(exceptionClassName);
    jmethodID ctor = env->GetMethodID(exceptionClass, "<init>", "(I)V");
    env->Throw(static_cast<jthrowable>(env->NewObject(exceptionClass, ctor, returnCode)));
    return returnCode;
}

class GitBuf
{
    git_buf buf_;
public:
    explicit GitBuf() : buf_({}) {
    }

    git_buf* Buffer() { return &buf_; }

    std::string String() const {
        return std::string(buf_.ptr, buf_.size);
    }

    ~GitBuf() {
        git_buf_dispose(&buf_);
    }
};

class CheckoutProgressContext
{
    JNIEnv *env_;
    jobject progress_;
    jfieldID completedSteps_;
    jfieldID totalSteps_;
public:
    CheckoutProgressContext(JNIEnv *env, jclass type, jobject progress) :
        env_(env),
        progress_(progress)
    {
        completedSteps_ = env->GetFieldID(type, "completedSteps", "J");
        totalSteps_ = env->GetFieldID(type, "totalSteps", "J");
    }

    void SetCompletedSteps(long value) { env_->SetLongField(progress_, completedSteps_, value); }
    void SetTotalSteps(long value) { env_->SetLongField(progress_, totalSteps_, value); }
};

class FetchProgressContext
{
    JNIEnv *env_;
    jobject progress_;
    jfieldID totalObjects_;
    jfieldID indexedObjects_;
    jfieldID receivedObjects_;
    jfieldID localObjects_;
    jfieldID totalDeltas_;
    jfieldID indexedDeltas_;
    jfieldID receivedBytes_;
    jfieldID sidebandMessage_;
public:
    FetchProgressContext(JNIEnv *env, jclass type, jobject progress) :
        env_(env),
        progress_(progress)
    {
        totalObjects_ = env->GetFieldID(type, "totalObjects", "J");
        indexedObjects_ = env->GetFieldID(type, "indexedObjects", "J");
        receivedObjects_ = env->GetFieldID(type, "receivedObjects", "J");
        localObjects_ = env->GetFieldID(type, "localObjects", "J");
        totalDeltas_ = env->GetFieldID(type, "totalDeltas", "J");
        indexedDeltas_ = env->GetFieldID(type, "indexedDeltas", "J");
        receivedBytes_ = env->GetFieldID(type, "receivedBytes", "J");
        sidebandMessage_ = env->GetFieldID(type, "sidebandMessage", "Ljava/lang/String;");
    }

    void SetTotalObjects(long value) { env_->SetLongField(progress_, totalObjects_, value); }
    void SetIndexedObjects(long value) { env_->SetLongField(progress_, indexedObjects_, value); }
    void SetReceivedObjects(long value) { env_->SetLongField(progress_, receivedObjects_, value); }
    void SetLocalObjects(long value) { env_->SetLongField(progress_, localObjects_, value); }
    void SetTotalDeltas(long value) { env_->SetLongField(progress_, totalDeltas_, value); }
    void SetIndexedDeltas(long value) { env_->SetLongField(progress_, indexedDeltas_, value); }
    void SetReceivedBytes(long value) { env_->SetLongField(progress_, receivedBytes_, value); }
    void SetSideBandMessage(const std::string& str) { env_->SetObjectField(progress_, sidebandMessage_, env_->NewStringUTF(str.c_str())); }
};

class Consumer
{
    JNIEnv *env_;
    jobject consumerObject_;
    jmethodID acceptMethod_;
public:
    Consumer(JNIEnv *env, jobject consumer) :
        env_(env),
        consumerObject_(consumer)
    {
        acceptMethod_ = env->GetMethodID(env->GetObjectClass(consumer), "accept", "(Ljava/lang/Object;)V");
    }

    void Accept(jobject obj) { env_->CallVoidMethod(consumerObject_, acceptMethod_, obj);  }
};

template <typename TContext, const char* ProgressClassName>
class ProgressReporter
{
    jobject progressObject_;
    std::unique_ptr<TContext> context_;
    std::unique_ptr<Consumer> consumer_;
public:
    ProgressReporter(JNIEnv *env, jobject progressConsumer) :
            consumer_(std::make_unique<Consumer>(env, progressConsumer))
    {
        //"io/github/sh4/zabuton/git/FetchProgress"
        jclass progressClass = env->FindClass(ProgressClassName);
        jmethodID ctor = env->GetMethodID(progressClass, "<init>", "()V");
        progressObject_ = env->NewObject(progressClass, ctor, nullptr);
        context_ = std::make_unique<TContext>(env, progressClass, progressObject_);
    }

    TContext* GetContext() const { return context_.get(); }

    void Accept() { consumer_->Accept(progressObject_); }
};

template <typename T>
void CheckoutProgressHandler(const char *path, size_t completed_steps, size_t total_steps, void *payload)
{
    auto p = reinterpret_cast<T*>(payload);
    assert(p != nullptr);
    p->GetContext()->SetCompletedSteps(completed_steps);
    p->GetContext()->SetTotalSteps(total_steps);
    p->Accept();
}

char CheckoutProgressName[] = "io/github/sh4/zabuton/git/CheckoutProgress";
char FetchProgressName[] = "io/github/sh4/zabuton/git/FetchProgress";
char ResetProgressName[] = "io/github/sh4/zabuton/git/ResetProgress";

using CheckoutProgressReporter = ProgressReporter<CheckoutProgressContext, CheckoutProgressName>;
using FetchProgressReporter = ProgressReporter<FetchProgressContext, FetchProgressName>;
using ResetProgressReporter = ProgressReporter<CheckoutProgressContext, ResetProgressName>;

class CloneProgressReporter
{
    jobject cloneProgress_;
    std::unique_ptr<FetchProgressContext> fetchProgress_;
    std::unique_ptr<CheckoutProgressContext> checkoutProgress_;
    std::unique_ptr<Consumer> consumer_;
public:
    CloneProgressReporter(JNIEnv *env, jobject progressConsumer) :
        consumer_(std::make_unique<Consumer>(env, progressConsumer))
    {
        jclass cloneProgressClass = env->FindClass("io/github/sh4/zabuton/git/CloneProgress");
        jmethodID ctor = env->GetMethodID(cloneProgressClass, "<init>", "()V");
        cloneProgress_ = env->NewObject(cloneProgressClass, ctor, nullptr);
        fetchProgress_ = std::make_unique<FetchProgressContext>(env, cloneProgressClass, cloneProgress_);
        checkoutProgress_ = std::make_unique<CheckoutProgressContext>(env, cloneProgressClass, cloneProgress_);
    }

    FetchProgressContext* GetFetchProgress() const { return fetchProgress_.get(); }
    CheckoutProgressContext* GetCheckoutProgress() const { return checkoutProgress_.get(); }

    void Accept() { consumer_->Accept(cloneProgress_); }
};

git_repository* GetGitRepository(JNIEnv *env, jobject this_)
{
    jfieldID handleField = env->GetFieldID(env->GetObjectClass(this_), "repositoryHandle", "J");
    git_repository *repo = reinterpret_cast<git_repository*>(env->GetLongField(this_, handleField));
    return repo;
}

bool EnsureParseGitRestType(git_reset_t *outResetType, JNIEnv *env, jobject resetKind_)
{
    jclass resetKindClass = env->GetObjectClass(resetKind_);
    const char* resetTypeStrings[] = { "SOFT", "MIXED", "HARD", nullptr };
    for (const char** p = resetTypeStrings; *p; p++) {
        jobject resetTypeObject = env->GetStaticObjectField(
                resetKindClass,
                env->GetStaticFieldID(resetKindClass, *p, "Lio/github/sh4/zabuton/git/ResetKind;"));
        if (env->IsSameObject(resetKind_, resetTypeObject))
        {
            *outResetType = static_cast<git_reset_t>(p - resetTypeStrings + 1);
            return true;
        }
    }
    env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
            "Unknown enum object in io.github.sh4.zabuton.git.ResetKind.");
    return false;
}

const char* GetCanonicalReferenceName(git_reference *ref)
{
    switch (git_reference_type(ref)) {
        case GIT_REFERENCE_SYMBOLIC:
            return git_reference_symbolic_target(ref);
        case GIT_REFERENCE_DIRECT:
            return git_reference_name(ref);
        default:
            return nullptr;
    }
}

std::string GetBranchReferenceName(JNIEnv* env, git_reference* ref)
{
    if (git_reference_type(ref) == GIT_REFERENCE_DIRECT) {
        const char* resoledRefName;
        ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(env, git_branch_name(&resoledRefName, ref), std::string());
        return resoledRefName;
    } else {
        const char* refname = GetCanonicalReferenceName(ref);
        return refname;
    }
}

std::string GetLocalReferenceNameFromRemoteName(JNIEnv *env, git_repository *repo,
                                                const char *canonicalName)
{
    GitBuf buf;
    ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(env,
            git_branch_remote_name(buf.Buffer(), repo, canonicalName), std::string());
    std::string remoteName = std::move(buf.String());
    git_remote *remote;
    ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(env,
            git_remote_lookup(&remote, repo, remoteName.c_str()), std::string());
    ZABUTON_MAKE_SCOPE([&]() { git_remote_free(remote); });
    const git_refspec* spec = git_remote_get_refspec(remote, 0);
    ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(env,
            git_refspec_rtransform(buf.Buffer(), spec, canonicalName), std::string());
    return buf.String();
}

int GetBranchReferences(std::vector<git_reference*>* outReferences, git_repository* repo, git_branch_t branchType)
{
    assert(repo != nullptr);

    git_branch_iterator *iter = nullptr;
    {
        int r = git_branch_iterator_new(&iter, repo, branchType);
        if (r != 0) {
            return r;
        }
    }
    ZABUTON_MAKE_SCOPE([&]() { git_branch_iterator_free(iter); });
    for (;;) {
        git_branch_t branchType;
        git_reference* branchRef;
        int r = git_branch_next(&branchRef, &branchType, iter);
        if (r == GIT_ITEROVER) {
            break;
        } else if (r != 0) {
            return r;
        }
        outReferences->push_back(branchRef);
    }
    return 0;
}

jobjectArray GetBranchReferenceNameArray(JNIEnv* env, git_repository* repo, git_branch_t branchType)
{
    std::vector<git_reference*> references;
    ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(
            env,
            GetBranchReferences(&references, repo, branchType),
            nullptr);

    jobjectArray refArray =
            env->NewObjectArray(static_cast<jsize>(references.size()), env->FindClass("java/lang/String"), nullptr);

    int i = 0;
    for (git_reference* ref : references) {
        std::string referenceName = GetBranchReferenceName(env, ref);
        if (referenceName.empty()) {
            return nullptr;
        }
        jstring refNameObject = env->NewStringUTF(referenceName.c_str());
        env->SetObjectArrayElement(refArray, i, refNameObject);
        i++;
    }

    return refArray;
}

jobjectArray GetTagReferenceNameArray(JNIEnv* env, git_repository* repo)
{
    std::vector<std::string> tags;

    ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(
            env,
            git_tag_foreach(repo, [](const char *name, git_oid *oid, void *payload) {
                auto t = reinterpret_cast<decltype(tags)*>(payload);
                assert(t != nullptr);
                t->push_back(std::string(name));
                return 0;
            }, &tags),
            nullptr);

    jobjectArray tagArray =
            env->NewObjectArray(static_cast<jsize>(tags.size()), env->FindClass("java/lang/String"), nullptr);
    int i = 0;
    for (auto& name : tags) {
        jstring refNameObject = env->NewStringUTF(name.c_str());
        env->SetObjectArrayElement(tagArray, i++, refNameObject);
    }
    return tagArray;
}

jobject GetUserObject(JNIEnv *env, const git_signature *sig) {
    jstring name;
    jstring email;
    jobject whenSignature = nullptr;

    if (sig) {
        name = env->NewStringUTF(sig->name);
        email = env->NewStringUTF(sig->email);
        jclass dateClass = env->FindClass("java/util/Date");
        jmethodID dateCtor = env->GetMethodID(dateClass, "<init>", "(J)V");
        const int64_t milliseconds = 1000LL;
        jlong date = sig->when.time + (sig->when.offset * sig->when.sign) * milliseconds;
        whenSignature = env->NewObject(dateClass, dateCtor, date);
    } else {
        name = env->NewStringUTF("");
        email = env->NewStringUTF("");
    }

    jclass userClass = env->FindClass("io/github/sh4/zabuton/git/User");
    jmethodID userCtor = env->GetMethodID(userClass, "<init>",
                                          "(Ljava/lang/String;Ljava/lang/String;Ljava/util/Date;)V");
    return env->NewObject(userClass, userCtor, name, email, whenSignature);
}

jobject GetCommitObject(JNIEnv *env, git_commit* commit) {
    jobject parentIdList;
    {
        unsigned int parents = git_commit_parentcount(commit);
        jobjectArray parentsIds =
                env->NewObjectArray(static_cast<jsize>(parents), env->FindClass("java/lang/String"), nullptr);
        if (parents > 0) {
            char buf[GIT_OID_HEXSZ + 1];
            for (unsigned int i = 0; i < parents; i++) {
                git_oid_tostr(buf, GIT_OID_HEXSZ, git_commit_parent_id(commit, i));
                jstring commitId = env->NewStringUTF(buf);
                env->SetObjectArrayElement(parentsIds, i, commitId);
            }
        }

        jclass arraysClass = env->FindClass("java/util/Arrays");
        jmethodID asListMethod = env->GetStaticMethodID(arraysClass, "asList", "([Ljava/lang/Object;)Ljava/util/List;");
        parentIdList = env->CallStaticObjectMethod(arraysClass, asListMethod, parentsIds);
    }

    jobject author = GetUserObject(env, git_commit_author(commit));
    jobject committer = GetUserObject(env, git_commit_committer(commit));
    jstring message = env->NewStringUTF(git_commit_message(commit));

    jclass commitObjectClass = env->FindClass("io/github/sh4/zabuton/git/CommitObject");
    jmethodID commitObjectCtor = env->GetMethodID(commitObjectClass, "<init>",
            "(Ljava/util/List;Lio/github/sh4/zabuton/git/User;Lio/github/sh4/zabuton/git/User;Ljava/lang/String;)V");

    return env->NewObject(commitObjectClass, commitObjectCtor, parentIdList, author, committer, message);
}

} // anonymous namespace

extern "C"
JNIEXPORT void JNICALL
Java_io_github_sh4_zabuton_git_LibGit2_init(JNIEnv *env, jclass /*type*/, jstring sslCertsFile_)
{
    const char *sslCertsFile = env->GetStringUTFChars(sslCertsFile_, 0);
    ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_libgit2_init());
    ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_libgit2_opts(GIT_OPT_SET_SSL_CERT_LOCATIONS, sslCertsFile, nullptr));
}

extern "C"
JNIEXPORT jobject JNICALL
Java_io_github_sh4_zabuton_git_Repository_clone(JNIEnv *env, jclass type, jstring url_, jstring clonePath_, jobject progressConsumer)
{
    const char *url = env->GetStringUTFChars(url_, 0);
    const char *clonePath = env->GetStringUTFChars(clonePath_, 0);

    auto ctx = std::make_unique<CloneProgressReporter>(env, progressConsumer);
    git_clone_options opts = GIT_CLONE_OPTIONS_INIT;
    opts.checkout_opts.checkout_strategy = GIT_CHECKOUT_SAFE;
    opts.checkout_opts.progress_cb = [](const char *path, size_t completed_steps, size_t total_steps, void *payload) -> void {
        auto p = reinterpret_cast<CloneProgressReporter*>(payload);
        assert(p != nullptr);
        p->GetCheckoutProgress()->SetCompletedSteps(completed_steps);
        p->GetCheckoutProgress()->SetTotalSteps(total_steps);
        p->Accept();
    };
    opts.checkout_opts.progress_payload = ctx.get();
    opts.checkout_opts.disable_filters = 1;
    opts.fetch_opts.callbacks.transfer_progress = [](const git_transfer_progress *stats, void *payload) -> int {
        auto p = reinterpret_cast<CloneProgressReporter*>(payload);
        assert(p != nullptr);
        p->GetFetchProgress()->SetIndexedDeltas(stats->indexed_deltas);
        p->GetFetchProgress()->SetIndexedObjects(stats->indexed_objects);
        p->GetFetchProgress()->SetLocalObjects(stats->local_objects);
        p->GetFetchProgress()->SetReceivedBytes(stats->received_bytes);
        p->GetFetchProgress()->SetReceivedObjects(stats->received_objects);
        p->GetFetchProgress()->SetTotalDeltas(stats->total_deltas);
        p->GetFetchProgress()->SetTotalObjects(stats->total_objects);
        p->Accept();
        return 0;
    };
    opts.fetch_opts.callbacks.sideband_progress = [](const char *str, int len, void *payload) -> int {
        auto p = reinterpret_cast<CloneProgressReporter*>(payload);
        assert(p != nullptr);
        if (len > 0) {
            p->GetFetchProgress()->SetSideBandMessage(std::string(str, static_cast<size_t>(len)));
        }
        return 0;
    };
    opts.fetch_opts.callbacks.payload = ctx.get();

    ctx->Accept();
    git_repository *repo = nullptr;
    ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(env, git_clone(&repo, url, clonePath, &opts), nullptr);
    jmethodID ctor = env->GetMethodID(type, "<init>", "(J)V");
    jobject repository = env->NewObject(type, ctor, reinterpret_cast<jlong>(repo));
    return repository;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_io_github_sh4_zabuton_git_Repository_open(JNIEnv *env, jclass type, jstring repoPath_)
{
    const char *repoPath = env->GetStringUTFChars(repoPath_, 0);
    git_repository *repo = nullptr;
    ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(env, git_repository_open(&repo, repoPath), nullptr);
    jmethodID ctor = env->GetMethodID(type, "<init>", "(J)V");
    jobject repository = env->NewObject(type, ctor, reinterpret_cast<jlong>(repo));
    return repository;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_sh4_zabuton_git_Repository_checkout(JNIEnv *env, jobject this_, jstring refspec_, jobject progressConsumer)
{
    const char *refspec = env->GetStringUTFChars(refspec_, 0);

    git_repository *repo = GetGitRepository(env, this_);
    assert(repo != nullptr);

    auto ctx = std::make_unique<CheckoutProgressReporter>(env, progressConsumer);

    git_checkout_options opts = GIT_CHECKOUT_OPTIONS_INIT;
    opts.checkout_strategy = GIT_CHECKOUT_SAFE;
    opts.progress_cb = CheckoutProgressHandler<CheckoutProgressReporter>;
    opts.progress_payload = ctx.get();
    opts.disable_filters = 1;

    git_annotated_commit *commit = nullptr;

    {
        git_reference *ref = nullptr;

        if (git_reference_dwim(&ref, repo, refspec) == GIT_OK) {
            ZABUTON_MAKE_SCOPE([&]() { git_reference_free(ref); });
            ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_annotated_commit_from_ref(&commit, repo, ref));
        } else {
            git_object *obj = nullptr;
            ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_revparse_single(&obj, repo, refspec));
            ZABUTON_MAKE_SCOPE([&]() { git_object_free(obj); });
            ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_annotated_commit_lookup(&commit, repo, git_object_id(obj)));
        }
    }

    git_commit *targetCommit = nullptr;
    ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_commit_lookup(&targetCommit, repo, git_annotated_commit_id(commit)));
    ZABUTON_MAKE_SCOPE([&]() { git_commit_free(targetCommit); });

    ctx->Accept();
    ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_checkout_tree(repo, reinterpret_cast<const git_object*>(targetCommit), &opts));

    const char* canonicalName = git_annotated_commit_ref(commit);
    const char* remoteRefPrefix = "refs/remotes/";
    std::string refName = strncmp(remoteRefPrefix, canonicalName, strlen(remoteRefPrefix)) == 0
                          ? GetLocalReferenceNameFromRemoteName(env, repo, canonicalName)
                          : canonicalName;
    if (refName.empty()) {
        return;
    }

    if (git_annotated_commit_ref(commit)) {
        git_reference* newBranchRef = nullptr;
        const char* headsRefPrefix = "refs/heads/";
        std::string localRefName = refName.substr(strlen(headsRefPrefix));
        int r = git_branch_lookup(&newBranchRef, repo, localRefName.c_str(), GIT_BRANCH_LOCAL);
        if (r == 0) {
            git_reference_free(newBranchRef);
        } else if (r == GIT_ENOTFOUND) {
            ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_branch_create(
                    &newBranchRef,
                    repo,
                    refName.substr(strlen("refs/heads/")).c_str(),
                    targetCommit, 0));
        } else {
            ensureNoErrorLibGit2(env, r);
            return;
        }
        ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_repository_set_head(repo, refName.c_str()));
    } else {
        ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_repository_set_head_detached_from_annotated(repo, commit));
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_sh4_zabuton_git_Repository_destroy(JNIEnv *env, jobject this_)
{
    git_repository *repo = GetGitRepository(env, this_);
    if (repo != nullptr) {
        git_repository_free(repo);
        jfieldID handleField = env->GetFieldID(env->GetObjectClass(this_), "repositoryHandle", "J");
        env->SetLongField(this_, handleField, 0);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_sh4_zabuton_git_Repository_fetch(JNIEnv *env, jobject this_, jstring remoteName_, jobject progressConsumer)
{
    git_repository *repo = GetGitRepository(env, this_);
    assert(repo != nullptr);

    auto ctx = std::make_unique<FetchProgressReporter>(env, progressConsumer);

    const char *remoteName = env->GetStringUTFChars(remoteName_, 0);
    git_remote *remote = nullptr;
    ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_remote_lookup(&remote, repo, remoteName));
    git_fetch_options opts = GIT_FETCH_OPTIONS_INIT;
    opts.callbacks.transfer_progress = [](const git_transfer_progress *stats, void *payload) -> int {
        auto p = reinterpret_cast<FetchProgressReporter*>(payload);
        assert(p != nullptr);
        p->GetContext()->SetIndexedDeltas(stats->indexed_deltas);
        p->GetContext()->SetIndexedObjects(stats->indexed_objects);
        p->GetContext()->SetLocalObjects(stats->local_objects);
        p->GetContext()->SetReceivedBytes(stats->received_bytes);
        p->GetContext()->SetReceivedObjects(stats->received_objects);
        p->GetContext()->SetTotalDeltas(stats->total_deltas);
        p->GetContext()->SetTotalObjects(stats->total_objects);
        return 0; // Note: Return a value less than zero to cancel the transfer.
    };
    opts.callbacks.sideband_progress = [](const char *str, int len, void *payload) -> int {
        auto p = reinterpret_cast<FetchProgressReporter*>(payload);
        assert(p != nullptr);
        if (len > 0) {
            p->GetContext()->SetSideBandMessage(std::string(str, static_cast<size_t>(len)));
        }
        return 0; // Note:  Return a negative value to cancel the network operation.
    };
    opts.download_tags = GIT_REMOTE_DOWNLOAD_TAGS_AUTO;
    opts.callbacks.payload = ctx.get();
    ctx->Accept();
    ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_remote_fetch(remote, nullptr, &opts, nullptr));
}


extern "C"
JNIEXPORT void JNICALL
Java_io_github_sh4_zabuton_git_Repository_reset(JNIEnv *env, jobject this_, jobject resetKind_, jobject progressConsumer)
{
    git_reset_t resetType;
    if (!EnsureParseGitRestType(&resetType, env, resetKind_)) {
        return;
    }

    git_repository *repo = GetGitRepository(env, this_);
    assert(repo != nullptr);

    auto ctx = std::make_unique<ResetProgressReporter>(env, progressConsumer);

    git_checkout_options opts = GIT_CHECKOUT_OPTIONS_INIT;
    opts.checkout_strategy = GIT_CHECKOUT_SAFE;
    opts.progress_cb = CheckoutProgressHandler<ResetProgressReporter>;
    opts.progress_payload = ctx.get();
    opts.disable_filters = 1;
    git_reference *headRef = nullptr;
    ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_repository_head(&headRef, repo));
    ZABUTON_MAKE_SCOPE([&]() { git_reference_free(headRef); });
    const git_oid* headOid = git_reference_target(headRef);
    git_commit* headCommit = nullptr;
    ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_commit_lookup(&headCommit, repo, headOid));
    ZABUTON_MAKE_SCOPE([&]() { git_commit_free(headCommit); });
    ctx->Accept();
    ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_reset(repo, reinterpret_cast<const git_object*>(headCommit), resetType, &opts));
}

extern "C"
JNIEXPORT jobject JNICALL
Java_io_github_sh4_zabuton_git_Repository_getHeadName(JNIEnv *env, jobject this_)
{
    git_repository *repo = GetGitRepository(env, this_);
    assert(repo != nullptr);

    git_reference* headRef;
    ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(env, git_repository_head(&headRef, repo), nullptr);
    ZABUTON_MAKE_SCOPE([&]() { git_reference_free(headRef); });
    std::string referenceName = GetBranchReferenceName(env, headRef);
    if (referenceName.empty()) {
        return nullptr;
    }
    return env->NewStringUTF(referenceName.c_str());
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_io_github_sh4_zabuton_git_Repository_getLocalBranchNames(JNIEnv *env, jobject this_)
{
    git_repository *repo = GetGitRepository(env, this_);
    jobjectArray refArray = GetBranchReferenceNameArray(env, repo, GIT_BRANCH_LOCAL);
    return refArray;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_io_github_sh4_zabuton_git_Repository_getRemoteBranchNames(JNIEnv *env, jobject this_)
{
    git_repository *repo = GetGitRepository(env, this_);
    jobjectArray refArray = GetBranchReferenceNameArray(env, repo, GIT_BRANCH_REMOTE);
    return refArray;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_io_github_sh4_zabuton_git_Repository_getTagNames(JNIEnv *env, jobject this_)
{
    git_repository *repo = GetGitRepository(env, this_);
    jobjectArray refArray = GetTagReferenceNameArray(env, repo);
    return refArray;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_io_github_sh4_zabuton_git_Repository_getRemotes(JNIEnv *env, jobject this_)
{
    git_repository *repo = GetGitRepository(env, this_);

    git_strarray remotes = {0};
    ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(env, git_remote_list(&remotes, repo), nullptr);
    ZABUTON_MAKE_SCOPE([&] { git_strarray_free(&remotes); });

    jclass remoteClass = env->FindClass("io/github/sh4/zabuton/git/Remote");
    jmethodID remoteClassCtor = env->GetMethodID(remoteClass, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    jobjectArray remoteArray = env->NewObjectArray(static_cast<int>(remotes.count), remoteClass, nullptr);
    for (int i = 0; i < static_cast<int>(remotes.count); i++)
    {
        git_remote* remote = {0};
        ZABUTON_ENSURE_LIBGIT2_NOERROR_WITH_RETURN(env, git_remote_lookup(&remote, repo, remotes.strings[i]), nullptr);
        ZABUTON_MAKE_SCOPE([&] { git_remote_free(remote); });

        jstring name = env->NewStringUTF(remotes.strings[i]);
        jstring fetchUrl = env->NewStringUTF(git_remote_url(remote));
        jstring pushUrl = env->NewStringUTF(git_remote_pushurl(remote));

        jobject remoteObject = env->NewObject(remoteClass, remoteClassCtor, name, fetchUrl, pushUrl);
        env->SetObjectArrayElement(remoteArray, i, remoteObject);
    }
    return remoteArray;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_sh4_zabuton_git_Repository_log(JNIEnv *env, jobject this_, jobject callback)
{
    git_repository *repo = GetGitRepository(env, this_);
    git_revwalk *walker = nullptr;

    ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_revwalk_new(&walker, repo));
    ZABUTON_MAKE_SCOPE([&]() { git_revwalk_free(walker); });
    git_revwalk_sorting(walker, GIT_SORT_TIME);

    ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_revwalk_push_head(walker));

    jclass functionClass = env->FindClass("java/util/function/Function");
    jmethodID applyMethod = env->GetMethodID(functionClass, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");

    git_oid oid;
    git_commit *commit = nullptr;
    ZABUTON_MAKE_SCOPE([&]() { git_commit_free(commit); });
    jclass boolClass = env->FindClass("java/lang/Boolean");
    jmethodID boolValueMethod = env->GetMethodID(boolClass, "booleanValue", "()Z");
    while(!git_revwalk_next(&oid, walker)) {
        if (commit != nullptr) {
            git_commit_free(commit);
            commit = nullptr;
        }
        ZABUTON_ENSURE_LIBGIT2_NOERROR(env, git_commit_lookup(&commit, repo, &oid));
        jobject commitObject = GetCommitObject(env, commit);
        jobject r = env->CallObjectMethod(callback, applyMethod, commitObject);
        if (r != nullptr && env->IsInstanceOf(r, boolClass)) {
            if (!env->CallBooleanMethod(r, boolValueMethod)) {
                break;
            }
        }
   }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_github_sh4_zabuton_git_LibGit2Exception_getLastError(JNIEnv *env, jclass /*type*/)
{
    std::string message;
    const git_error *e = giterr_last();
    message += e->message;
    if (message.empty()) {
        return env->NewStringUTF("");
    }
    message += ' ';
    message += '(';
    message += std::to_string(e->klass);
    message += ')';
    return env->NewStringUTF(message.c_str());
}
