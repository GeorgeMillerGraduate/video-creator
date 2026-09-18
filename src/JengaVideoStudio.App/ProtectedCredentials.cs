using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using JengaVideoStudio.Core;

namespace JengaVideoStudio.App;

/// <summary>
/// Stores small application credentials using Windows DPAPI.
///
/// Credentials are encrypted for the current Windows user and cannot normally
/// be decrypted by another Windows account.
/// </summary>
public sealed class ProtectedCredentials : ICredentialStore
{
    private const int CryptProtectUiForbidden = 0x1;

    private const int MaximumCredentialBytes =
        4 * 1024 * 1024;

    private const string Description =
        "Jenga Video Studio";

    // ========================================================================
    // WINDOWS DPAPI
    // ========================================================================

    [StructLayout(LayoutKind.Sequential)]
    private struct DataBlob
    {
        public int Length;
        public IntPtr Data;
    }

    [DllImport(
        "crypt32.dll",
        SetLastError = true,
        CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CryptProtectData(
        ref DataBlob input,
        string? description,
        IntPtr optionalEntropy,
        IntPtr reserved,
        IntPtr prompt,
        int flags,
        out DataBlob output);

    [DllImport(
        "crypt32.dll",
        SetLastError = true,
        CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CryptUnprotectData(
        ref DataBlob input,
        IntPtr description,
        IntPtr optionalEntropy,
        IntPtr reserved,
        IntPtr prompt,
        int flags,
        out DataBlob output);

    [DllImport(
        "kernel32.dll",
        SetLastError = true)]
    private static extern IntPtr LocalFree(
        IntPtr memory);

    // ========================================================================
    // SAVE
    // ========================================================================

    public void Save(
        string key,
        string value)
    {
        ValidateKey(key);

        ArgumentNullException.ThrowIfNull(value);

        var plaintext =
            Encoding.UTF8.GetBytes(value);

        try
        {
            if (plaintext.Length >
                MaximumCredentialBytes)
            {
                throw new InvalidDataException(
                    "Credential data is unexpectedly large.");
            }

            var encrypted =
                Transform(
                    plaintext,
                    protect: true);

            try
            {
                var path =
                    PathFor(key);

                var temporary =
                    path + ".tmp";

                try
                {
                    /*
                     * Write to a temporary file first so a crash cannot
                     * leave half of a credential file behind.
                     */
                    File.WriteAllBytes(
                        temporary,
                        encrypted);

                    File.Move(
                        temporary,
                        path,
                        true);
                }
                catch
                {
                    TryDelete(
                        temporary);

                    throw;
                }
            }
            finally
            {
                CryptographicOperations.ZeroMemory(
                    encrypted);
            }
        }
        finally
        {
            /*
             * The OAuth token existed in this managed byte array in plaintext.
             * Clear it as soon as it is no longer required.
             */
            CryptographicOperations.ZeroMemory(
                plaintext);
        }
    }

    // ========================================================================
    // READ
    // ========================================================================

    public string? Read(
        string key)
    {
        ValidateKey(key);

        var path =
            PathFor(key);

        if (!File.Exists(path))
            return null;

        byte[] encrypted;

        try
        {
            var info =
                new FileInfo(path);

            if (info.Length <= 0)
            {
                throw new InvalidDataException(
                    "The stored credential file is empty.");
            }

            if (info.Length >
                MaximumCredentialBytes)
            {
                throw new InvalidDataException(
                    "The stored credential file is unexpectedly large.");
            }

            encrypted =
                File.ReadAllBytes(path);
        }
        catch (Exception e)
            when (
                e is IOException
                or UnauthorizedAccessException)
        {
            throw new InvalidOperationException(
                "The saved credential could not be read.",
                e);
        }

        try
        {
            var plaintext =
                Transform(
                    encrypted,
                    protect: false);

            try
            {
                if (plaintext.Length == 0)
                {
                    throw new InvalidDataException(
                        "The decrypted credential is empty.");
                }

                return new UTF8Encoding(
                        encoderShouldEmitUTF8Identifier: false,
                        throwOnInvalidBytes: true)
                    .GetString(plaintext);
            }
            catch (DecoderFallbackException e)
            {
                throw new InvalidDataException(
                    "The saved credential did not contain valid UTF-8 data.",
                    e);
            }
            finally
            {
                CryptographicOperations.ZeroMemory(
                    plaintext);
            }
        }
        catch (Win32Exception e)
        {
            throw new InvalidOperationException(
                "Windows could not decrypt the saved credential. " +
                "It may belong to another Windows account or may be damaged.",
                e);
        }
        finally
        {
            CryptographicOperations.ZeroMemory(
                encrypted);
        }
    }

    // ========================================================================
    // DELETE
    // ========================================================================

    public void Delete(
        string key)
    {
        ValidateKey(key);

        var path =
            PathFor(key);

        TryDelete(
            path);

        /*
         * Also remove a temporary file that could remain after an interrupted
         * save.
         */
        TryDelete(
            path + ".tmp");
    }

    // ========================================================================
    // DPAPI TRANSFORMATION
    // ========================================================================

    private static byte[] Transform(
        byte[] bytes,
        bool protect)
    {
        ArgumentNullException.ThrowIfNull(bytes);

        if (bytes.Length == 0)
        {
            throw new InvalidDataException(
                protect
                    ? "Cannot protect empty credential data."
                    : "Cannot decrypt empty credential data.");
        }

        if (bytes.Length >
            MaximumCredentialBytes)
        {
            throw new InvalidDataException(
                "Credential data is unexpectedly large.");
        }

        var input =
            new DataBlob();

        var output =
            new DataBlob();

        try
        {
            input.Length =
                bytes.Length;

            input.Data =
                Marshal.AllocHGlobal(
                    bytes.Length);

            Marshal.Copy(
                bytes,
                0,
                input.Data,
                bytes.Length);

            bool success;

            if (protect)
            {
                success =
                    CryptProtectData(
                        ref input,
                        Description,
                        IntPtr.Zero,
                        IntPtr.Zero,
                        IntPtr.Zero,
                        CryptProtectUiForbidden,
                        out output);
            }
            else
            {
                success =
                    CryptUnprotectData(
                        ref input,
                        IntPtr.Zero,
                        IntPtr.Zero,
                        IntPtr.Zero,
                        IntPtr.Zero,
                        CryptProtectUiForbidden,
                        out output);
            }

            if (!success)
            {
                throw new Win32Exception(
                    Marshal.GetLastWin32Error());
            }

            if (output.Data == IntPtr.Zero ||
                output.Length <= 0)
            {
                throw new InvalidDataException(
                    protect
                        ? "Windows returned no encrypted credential data."
                        : "Windows returned no decrypted credential data.");
            }

            if (output.Length >
                MaximumCredentialBytes)
            {
                throw new InvalidDataException(
                    "Windows returned an unexpectedly large credential.");
            }

            var result =
                new byte[output.Length];

            Marshal.Copy(
                output.Data,
                result,
                0,
                result.Length);

            return result;
        }
        finally
        {
            // Clear our unmanaged plaintext/ciphertext input buffer before
            // returning it to the process heap.
            if (input.Data != IntPtr.Zero)
            {
                ZeroUnmanagedMemory(
                    input.Data,
                    input.Length);

                Marshal.FreeHGlobal(
                    input.Data);
            }

            /*
             * DPAPI allocates output using LocalAlloc. Clear it before
             * LocalFree so decrypted credential material is not deliberately
             * left in the unmanaged allocation.
             */
            if (output.Data != IntPtr.Zero)
            {
                ZeroUnmanagedMemory(
                    output.Data,
                    output.Length);

                LocalFree(
                    output.Data);
            }
        }
    }

    // ========================================================================
    // STORAGE PATH
    // ========================================================================

    private static string PathFor(
        string key)
    {
        ValidateKey(key);

        var directory =
            Path.Combine(
                Settings.DataRoot,
                "credentials");

        Directory.CreateDirectory(
            directory);

        /*
         * Never use the credential name itself as a filename.
         *
         * This also prevents path traversal if another credential key is
         * introduced in future.
         */
        var filename =
            Util.Hash(key) +
            ".token";

        return Path.Combine(
            directory,
            filename);
    }

    private static void ValidateKey(
        string key)
    {
        if (string.IsNullOrWhiteSpace(key))
        {
            throw new ArgumentException(
                "Credential key cannot be empty.",
                nameof(key));
        }

        if (key.Length > 256)
        {
            throw new ArgumentException(
                "Credential key is unexpectedly long.",
                nameof(key));
        }
    }

    // ========================================================================
    // MEMORY / FILE CLEANUP
    // ========================================================================

    private static void ZeroUnmanagedMemory(
        IntPtr address,
        int length)
    {
        if (address == IntPtr.Zero ||
            length <= 0)
        {
            return;
        }

        /*
         * Credentials here are tiny. Writing in blocks avoids allocating
         * another buffer the size of the secret.
         */
        var zeros =
            new byte[Math.Min(
                length,
                4096)];

        var offset =
            0;

        while (offset < length)
        {
            var count =
                Math.Min(
                    zeros.Length,
                    length - offset);

            Marshal.Copy(
                zeros,
                0,
                IntPtr.Add(
                    address,
                    offset),
                count);

            offset +=
                count;
        }
    }

    private static void TryDelete(
        string path)
    {
        try
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
        catch (FileNotFoundException)
        {
        }
        catch (DirectoryNotFoundException)
        {
        }
    }
}