package moe.reimu.catshare.exceptions

import io.ktor.utils.io.CancellationException

class CancelledByUserException(val isRemote: Boolean) :
    CancellationException("Cancelled by user")